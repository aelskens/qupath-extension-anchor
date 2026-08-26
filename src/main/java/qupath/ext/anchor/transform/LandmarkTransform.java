package qupath.ext.anchor.transform;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * A fitted spatial transform mapping a point from the source image's coordinate space into the
 * target image's coordinate space. Implemented by both the linear family (affine/similarity/rigid)
 * and nonlinear transforms (TPS), so callers can treat them uniformly.
 */
public interface LandmarkTransform {

    TransformType getType();

    /** Map a source-space point into target space. */
    Point2D apply(Point2D sourcePoint);

    /**
     * Map an image(target)-space point back into source(reference)-space. Exact for linear transforms;
     * iterative for TPS. Returns {@code null} if the inverse cannot be computed at that point.
     */
    Point2D applyInverse(Point2D imagePoint);

    /**
     * The equivalent {@link AffineTransform}, present for the linear family and empty for nonlinear
     * transforms (TPS). Nonlinear transforms must be evaluated per point via {@link #apply(Point2D)}.
     */
    Optional<AffineTransform> asAffineTransform();

    /**
     * A local affine approximation of the transform about {@code at} (the linearization: value plus
     * Jacobian by finite differences). Exact for linear transforms; for nonlinear transforms (TPS)
     * it is accurate near {@code at}, letting the affine-based sync/overlay pipeline follow the warp
     * when recomputed as the view moves.
     */
    default AffineTransform localAffine(Point2D at) {
        double h = 1.0;
        Point2D p0 = apply(at);
        Point2D px = apply(new Point2D.Double(at.getX() + h, at.getY()));
        Point2D py = apply(new Point2D.Double(at.getX(), at.getY() + h));
        double a = (px.getX() - p0.getX()) / h;
        double c = (px.getY() - p0.getY()) / h;
        double b = (py.getX() - p0.getX()) / h;
        double d = (py.getY() - p0.getY()) / h;
        double tx = p0.getX() - (a * at.getX() + b * at.getY());
        double ty = p0.getY() - (c * at.getX() + d * at.getY());
        // AffineTransform arg order is (m00, m10, m01, m11, m02, m12) = (a, c, b, d, tx, ty).
        return new AffineTransform(a, c, b, d, tx, ty);
    }
}
