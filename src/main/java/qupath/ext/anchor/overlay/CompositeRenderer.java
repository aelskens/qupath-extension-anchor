package qupath.ext.anchor.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Optional;

import qupath.ext.anchor.transform.LandmarkTransform;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.images.servers.ImageServer;

/**
 * Renders a false-color composite of one or more aligned images into an off-screen buffer, covering
 * only the reference viewer's current view region.
 * <p>
 * Each layer is reduced to a per-pixel <b>intensity</b> (luminance of its rendered pixels), optionally
 * <b>inverted</b> (so a bright H&amp;E background drops out), multiplied by the layer tint and opacity,
 * and <b>additively</b> accumulated over black. Linear layers are drawn with the fast tile-cache path
 * ({@code paintRegionCompletely} + the layer affine). Nonlinear layers (TPS) are rendered to RGB via
 * the layer's {@code ImageDisplay} and then <b>forward-warped</b> per pixel through the transform, so
 * the composite shows a genuine elastic warp for brightfield and fluorescence alike.
 */
public final class CompositeRenderer {

    private CompositeRenderer() {}

    /**
     * Per-image render layer.
     *
     * @param server     the image
     * @param refToImage transform mapping reference-image pixels to this image's pixels
     * @param renderer   how to render the image to RGB (an {@code ImageDisplay})
     * @param tint       the color this image contributes
     * @param invert     invert intensity (for bright-background brightfield/RGB images)
     * @param opacity    weight of this layer in [0, 1]; 0 hides it
     */
    public record Layer(ImageServer<BufferedImage> server, LandmarkTransform refToImage,
                        ImageRenderer renderer, Color tint, boolean invert, double opacity, int z, int t) {}

    /** A landmark marker to draw on the composite, positioned in reference-image coordinates. */
    public record Marker(double refX, double refY, Color color) {}

    private static final long TIMEOUT_MS = 250;
    /** Cap on the intermediate warp-source buffer, to bound memory/time. */
    private static final int MAX_WARP_SOURCE = 3000;
    /** Output-pixel spacing of the warp displacement grid; the transform is evaluated only at nodes. */
    private static final int WARP_GRID_STEP = 8;

    public static BufferedImage render(DefaultImageRegionStore store,
                                       double centerX, double centerY, double downsample, double rotation,
                                       int width, int height, List<Layer> layers, List<Marker> markers) {
        int n = width * height;
        int[] accR = new int[n];
        int[] accG = new int[n];
        int[] accB = new int[n];

        AffineTransform base = viewerTransform(width, height, centerX, centerY, downsample, rotation);
        AffineTransform baseInv;
        try {
            baseInv = base.createInverse();
        } catch (NoninvertibleTransformException e) {
            return blackImage(width, height);
        }

        for (Layer layer : layers) {
            if (layer.opacity() <= 0)
                continue;
            BufferedImage layerImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Optional<AffineTransform> affine = layer.refToImage().asAffineTransform();
            boolean ok = affine.isPresent()
                    ? renderAffine(layerImg, store, base, affine.get(), downsample, width, height, layer)
                    : renderWarp(layerImg, store, base, baseInv, downsample, width, height, layer);
            if (ok)
                accumulate(layerImg, n, accR, accG, accB, layer);
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] outPx = pixels(out);
        for (int i = 0; i < n; i++) {
            int r = Math.min(255, accR[i]);
            int g = Math.min(255, accG[i]);
            int b = Math.min(255, accB[i]);
            outPx[i] = 0xff000000 | (r << 16) | (g << 8) | b; // opaque black background
        }

        drawMarkers(out, base, markers);
        return out;
    }

    /** Draw landmark markers (screen-fixed size) at their reference positions mapped through {@code base}. */
    private static void drawMarkers(BufferedImage out, AffineTransform base, List<Marker> markers) {
        if (markers == null || markers.isEmpty())
            return;
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int radius = 4;
            for (Marker m : markers) {
                Point2D p = base.transform(new Point2D.Double(m.refX(), m.refY()), null);
                int cx = (int) Math.round(p.getX());
                int cy = (int) Math.round(p.getY());
                g.setColor(java.awt.Color.BLACK);
                g.fillOval(cx - radius - 1, cy - radius - 1, 2 * (radius + 1), 2 * (radius + 1));
                g.setColor(m.color());
                g.fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            }
        } finally {
            g.dispose();
        }
    }

    /** Linear layer: draw the image through the affine via the shared tile cache. */
    private static boolean renderAffine(BufferedImage layerImg, DefaultImageRegionStore store,
                                        AffineTransform base, AffineTransform affine, double downsample,
                                        int width, int height, Layer layer) {
        Graphics2D g = layerImg.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(0, 0, width, height);
            AffineTransform t = new AffineTransform(base);
            t.concatenate(affine.createInverse());
            g.setTransform(t);
            double scale = Math.sqrt(Math.abs(affine.getDeterminant()));
            double layerDownsample = Math.max(downsample * scale, 0.25);
            store.paintRegionCompletely(layer.server(), g, g.getClip(), layer.z(), layer.t(),
                    layerDownsample, null, layer.renderer(), TIMEOUT_MS);
            return true;
        } catch (NoninvertibleTransformException e) {
            return false;
        } finally {
            g.dispose();
        }
    }

    /** Nonlinear layer: render the source region to RGB, then forward-warp it into the reference frame. */
    private static boolean renderWarp(BufferedImage layerImg, DefaultImageRegionStore store,
                                      AffineTransform base, AffineTransform baseInv, double downsample,
                                      int width, int height, Layer layer) {
        LandmarkTransform transform = layer.refToImage();

        // Bounding box in source-image pixels of the visible reference region, by mapping a grid of
        // buffer points -> reference -> source.
        double minSx = Double.POSITIVE_INFINITY, minSy = Double.POSITIVE_INFINITY;
        double maxSx = Double.NEGATIVE_INFINITY, maxSy = Double.NEGATIVE_INFINITY;
        Point2D ref = new Point2D.Double();
        for (int gy = 0; gy <= 8; gy++) {
            for (int gx = 0; gx <= 8; gx++) {
                baseInv.transform(new Point2D.Double(gx * width / 8.0, gy * height / 8.0), ref);
                Point2D s = transform.apply(ref);
                if (Double.isNaN(s.getX()) || Double.isNaN(s.getY()))
                    continue;
                minSx = Math.min(minSx, s.getX());
                minSy = Math.min(minSy, s.getY());
                maxSx = Math.max(maxSx, s.getX());
                maxSy = Math.max(maxSy, s.getY());
            }
        }
        if (minSx > maxSx || minSy > maxSy)
            return false;

        double secW = maxSx - minSx;
        double secH = maxSy - minSy;
        // Read downsample so the source buffer roughly matches the output resolution.
        double dsRead = Math.max(Math.max(secW / width, secH / height), 0.25);
        int rw = (int) Math.ceil(secW / dsRead);
        int rh = (int) Math.ceil(secH / dsRead);
        if (rw <= 0 || rh <= 0)
            return false;
        // Bound the intermediate buffer.
        double capScale = Math.max((double) rw / MAX_WARP_SOURCE, (double) rh / MAX_WARP_SOURCE);
        if (capScale > 1) {
            dsRead *= capScale;
            rw = (int) Math.ceil(secW / dsRead);
            rh = (int) Math.ceil(secH / dsRead);
        }

        BufferedImage src = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = src.createGraphics();
        try {
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.setClip(0, 0, rw, rh);
            AffineTransform st = new AffineTransform();
            st.scale(1.0 / dsRead, 1.0 / dsRead);
            st.translate(-minSx, -minSy);
            sg.setTransform(st);
            store.paintRegionCompletely(layer.server(), sg, sg.getClip(), layer.z(), layer.t(),
                    dsRead, null, layer.renderer(), TIMEOUT_MS);
        } finally {
            sg.dispose();
        }

        int[] srcPx = pixels(src);
        int[] outPx = pixels(layerImg);

        // Evaluate the (expensive, especially for TPS) transform only on a coarse grid of output
        // nodes, then bilinearly interpolate the source-sample coordinates for every pixel in between.
        // The warp is smooth, so this is visually indistinguishable but ~STEP^2 fewer transform calls.
        int cols = width / WARP_GRID_STEP + 2;
        int rows = height / WARP_GRID_STEP + 2;
        double[] nfi = new double[cols * rows];
        double[] nfj = new double[cols * rows];
        for (int gj = 0; gj < rows; gj++) {
            for (int gi = 0; gi < cols; gi++) {
                double ox = Math.min(gi * WARP_GRID_STEP, width);
                double oy = Math.min(gj * WARP_GRID_STEP, height);
                baseInv.transform(new Point2D.Double(ox + 0.5, oy + 0.5), ref);
                Point2D s = transform.apply(ref);
                int idx = gj * cols + gi;
                if (s == null || Double.isNaN(s.getX()) || Double.isNaN(s.getY())) {
                    nfi[idx] = Double.NaN;
                    nfj[idx] = Double.NaN;
                } else {
                    nfi[idx] = (s.getX() - minSx) / dsRead - 0.5;
                    nfj[idx] = (s.getY() - minSy) / dsRead - 0.5;
                }
            }
        }

        for (int y = 0; y < height; y++) {
            int gj = y / WARP_GRID_STEP;
            double ty = (y - gj * WARP_GRID_STEP) / (double) WARP_GRID_STEP;
            for (int x = 0; x < width; x++) {
                int gi = x / WARP_GRID_STEP;
                double tx = (x - gi * WARP_GRID_STEP) / (double) WARP_GRID_STEP;
                int i00 = gj * cols + gi;
                double a = nfi[i00], b = nfi[i00 + 1], c = nfi[i00 + cols], d = nfi[i00 + cols + 1];
                double e = nfj[i00], f = nfj[i00 + 1], g = nfj[i00 + cols], h = nfj[i00 + cols + 1];
                if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c) || Double.isNaN(d)) {
                    outPx[y * width + x] = 0;
                    continue;
                }
                double fi = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
                double fj = (e * (1 - tx) + f * tx) * (1 - ty) + (g * (1 - tx) + h * tx) * ty;
                outPx[y * width + x] = bilinear(srcPx, rw, rh, fi, fj);
            }
        }
        return true;
    }

    /** Accumulate one rendered (output-space) layer buffer into the additive tint accumulators. */
    private static void accumulate(BufferedImage layerImg, int n, int[] accR, int[] accG, int[] accB, Layer layer) {
        int[] px = pixels(layerImg);
        int tr = layer.tint().getRed(), tg = layer.tint().getGreen(), tb = layer.tint().getBlue();
        boolean invert = layer.invert();
        double op = layer.opacity();
        for (int i = 0; i < n; i++) {
            int argb = px[i];
            int a = (argb >>> 24) & 0xff;
            if (a == 0)
                continue;
            int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
            double lum = 0.299 * r + 0.587 * g + 0.114 * b;
            if (invert)
                lum = 255.0 - lum;
            double intensity = (lum / 255.0) * (a / 255.0) * op;
            accR[i] += (int) Math.round(tr * intensity);
            accG[i] += (int) Math.round(tg * intensity);
            accB[i] += (int) Math.round(tb * intensity);
        }
    }

    /** Bilinear sample of an ARGB int buffer; returns 0 (transparent) if fully outside. */
    private static int bilinear(int[] px, int w, int h, double fx, double fy) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        if (x0 < -1 || y0 < -1 || x0 >= w || y0 >= h)
            return 0;
        double dx = fx - x0;
        double dy = fy - y0;
        int a = 0, r = 0, g = 0, b = 0;
        double wsum = 0;
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                int xx = x0 + i, yy = y0 + j;
                if (xx < 0 || yy < 0 || xx >= w || yy >= h)
                    continue;
                double weight = (i == 0 ? 1 - dx : dx) * (j == 0 ? 1 - dy : dy);
                int argb = px[yy * w + xx];
                int pa = (argb >>> 24) & 0xff;
                if (pa == 0)
                    continue;
                a += (int) (pa * weight);
                r += (int) (((argb >> 16) & 0xff) * weight);
                g += (int) (((argb >> 8) & 0xff) * weight);
                b += (int) ((argb & 0xff) * weight);
                wsum += weight;
            }
        }
        if (wsum <= 0)
            return 0;
        return (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int[] pixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    private static BufferedImage blackImage(int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] px = pixels(out);
        for (int i = 0; i < px.length; i++)
            px[i] = 0xff000000;
        return out;
    }

    /** Reconstructs a QuPath viewer's image-to-component transform (see QuPathViewer.updateAffineTransform). */
    public static AffineTransform viewerTransform(int width, int height,
                                                   double centerX, double centerY, double downsample, double rotation) {
        AffineTransform t = new AffineTransform();
        t.translate(width / 2.0, height / 2.0);
        t.scale(1.0 / downsample, 1.0 / downsample);
        t.translate(-centerX, -centerY);
        if (rotation != 0)
            t.rotate(rotation, centerX, centerY);
        return t;
    }
}
