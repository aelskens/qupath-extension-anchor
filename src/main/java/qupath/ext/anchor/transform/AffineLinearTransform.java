package qupath.ext.anchor.transform;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * A linear (affine/similarity/rigid) transform stored as a 2x3 matrix:
 * <pre>
 *   x' = a*x + b*y + tx
 *   y' = c*x + d*y + ty
 * </pre>
 * All three linear {@link TransformType}s reduce to this representation, differing only in how the
 * coefficients are fitted.
 */
public final class AffineLinearTransform implements LandmarkTransform {

    private final TransformType type;
    private final double a, b, c, d, tx, ty;

    public AffineLinearTransform(TransformType type, double a, double b, double c, double d,
                                 double tx, double ty) {
        if (type == null || !type.isLinear())
            throw new IllegalArgumentException("AffineLinearTransform requires a linear TransformType, got " + type);
        this.type = type;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.tx = tx;
        this.ty = ty;
    }

    @Override
    public TransformType getType() {
        return type;
    }

    @Override
    public Point2D apply(Point2D sourcePoint) {
        double x = sourcePoint.getX();
        double y = sourcePoint.getY();
        return new Point2D.Double(a * x + b * y + tx, c * x + d * y + ty);
    }

    @Override
    public Optional<AffineTransform> asAffineTransform() {
        // java.awt.geom.AffineTransform constructor order is (m00, m10, m01, m11, m02, m12),
        // i.e. column-major: (a, c, b, d, tx, ty).
        return Optional.of(new AffineTransform(a, c, b, d, tx, ty));
    }

    @Override
    public Point2D applyInverse(Point2D imagePoint) {
        try {
            return new AffineTransform(a, c, b, d, tx, ty).createInverse().transform(imagePoint, null);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }

    public double a() { return a; }
    public double b() { return b; }
    public double c() { return c; }
    public double d() { return d; }
    public double tx() { return tx; }
    public double ty() { return ty; }

    @Override
    public String toString() {
        return String.format("AffineLinearTransform[%s: [%.6f %.6f %.3f; %.6f %.6f %.3f]]",
                type, a, b, tx, c, d, ty);
    }
}
