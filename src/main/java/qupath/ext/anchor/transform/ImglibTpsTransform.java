package qupath.ext.anchor.transform;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;

/**
 * Thin-plate spline warp backed by ImgLib2's {@link ThinplateSplineTransform} (pure-Java jitk-tps).
 * Nonlinear, so it has no single {@link AffineTransform}; the sync/overlay pipeline uses
 * {@link #localAffine(Point2D)} (inherited finite-difference linearization). The underlying
 * {@link RealTransform} is exposed for true elastic image warping (see the overlay renderer).
 */
public final class ImglibTpsTransform implements LandmarkTransform {

    private final ThinplateSplineTransform tps;
    private InvertibleRealTransform invertible; // lazily created for applyInverse

    private ImglibTpsTransform(ThinplateSplineTransform tps) {
        this.tps = tps;
    }

    /** Fit a TPS mapping source points to target points (matched by index). */
    public static ImglibTpsTransform fit(List<Point2D> source, List<Point2D> target) {
        int n = source.size();
        // ImgLib2 uses dimension-major arrays: p[dim][landmarkIndex].
        double[][] p = new double[2][n];
        double[][] q = new double[2][n];
        for (int i = 0; i < n; i++) {
            p[0][i] = source.get(i).getX();
            p[1][i] = source.get(i).getY();
            q[0][i] = target.get(i).getX();
            q[1][i] = target.get(i).getY();
        }
        return new ImglibTpsTransform(new ThinplateSplineTransform(p, q));
    }

    @Override
    public TransformType getType() {
        return TransformType.TPS;
    }

    @Override
    public Point2D apply(Point2D sourcePoint) {
        double[] src = {sourcePoint.getX(), sourcePoint.getY()};
        double[] tgt = new double[2];
        tps.apply(src, tgt);
        return new Point2D.Double(tgt[0], tgt[1]);
    }

    @Override
    public Optional<AffineTransform> asAffineTransform() {
        return Optional.empty();
    }

    @Override
    public Point2D applyInverse(Point2D imagePoint) {
        if (invertible == null)
            invertible = new WrappedIterativeInvertibleRealTransform<>(tps);
        double[] in = {imagePoint.getX(), imagePoint.getY()};
        double[] out = new double[2];
        try {
            invertible.applyInverse(out, in); // out = T^-1(in)
        } catch (RuntimeException e) {
            return null;
        }
        if (Double.isNaN(out[0]) || Double.isNaN(out[1]))
            return null;
        return new Point2D.Double(out[0], out[1]);
    }

    /** The underlying ImgLib2 transform (reference-image pixels -&gt; this image's pixels). */
    public RealTransform realTransform() {
        return tps;
    }
}
