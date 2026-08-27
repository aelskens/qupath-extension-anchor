package qupath.ext.anchor.overlay;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;

import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

/**
 * Draws the latest false-color composite (produced by {@link ColorProjectionManager}) directly into
 * the reference viewer, in place. The composite is rendered for a specific reference-frame view (its
 * {@code baseAtRender} transform, reference pixels -&gt; device at render time). At paint time the
 * buffer is re-placed through {@code currentBase * baseAtRender^-1}, so it tracks the image content as
 * the user pans/zooms/rotates (the last-rendered buffer stays pinned to the pixels it came from until
 * the next render lands), rather than floating in screen space.
 */
public class ColorProjectionOverlay extends AbstractOverlay {

    private volatile BufferedImage composite;
    private volatile AffineTransform baseAtRender;

    public ColorProjectionOverlay(QuPathViewer viewer) {
        super(viewer.getOverlayOptions());
    }

    /** Set the composite and the reference-frame-to-device transform it was rendered with. */
    public void setComposite(BufferedImage composite, AffineTransform baseAtRender) {
        this.composite = composite;
        this.baseAtRender = baseAtRender;
    }

    public void clear() {
        this.composite = null;
        this.baseAtRender = null;
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
                             ImageData<BufferedImage> imageData, boolean paintCompletely) {
        BufferedImage img = composite;
        AffineTransform base = baseAtRender;
        if (img == null || base == null)
            return;

        Graphics2D g = (Graphics2D) g2d.create();
        try {
            // g2d is reference-image pixels -> device now. Map render-time device -> now via base^-1.
            AffineTransform draw = new AffineTransform(g2d.getTransform());
            draw.concatenate(base.createInverse());
            g.setTransform(draw);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, null);
        } catch (NoninvertibleTransformException e) {
            // Degenerate transform; skip this paint.
        } finally {
            g.dispose();
        }
    }
}
