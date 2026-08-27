package qupath.ext.anchor.transform;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * Re-expresses a reference-to-image transform in a different viewer's frame. Given
 * {@code refToTarget} (reference pixels -&gt; the target viewer's image) and {@code refToImage}
 * (reference pixels -&gt; some image), this maps <b>target</b>-image pixels to that image:
 * <pre>target -&gt; reference (refToTarget^-1) -&gt; image (refToImage)</pre>
 * so the color composite can be rendered in any chosen viewer's frame, not just the reference's.
 * When {@code refToTarget} is the identity this reduces to {@code refToImage} unchanged.
 */
public final class ReframedTransform implements LandmarkTransform {

    private final LandmarkTransform refToTarget; // reference -> target image
    private final LandmarkTransform refToImage;  // reference -> this image

    public ReframedTransform(LandmarkTransform refToTarget, LandmarkTransform refToImage) {
        this.refToTarget = refToTarget;
        this.refToImage = refToImage;
    }

    @Override
    public TransformType getType() {
        return (refToTarget.getType().isLinear() && refToImage.getType().isLinear())
                ? TransformType.AFFINE : TransformType.TPS;
    }

    @Override
    public Point2D apply(Point2D targetPoint) {
        Point2D ref = refToTarget.applyInverse(targetPoint); // target -> reference
        if (ref == null)
            return null;
        return refToImage.apply(ref);                        // reference -> image
    }

    @Override
    public Point2D applyInverse(Point2D imagePoint) {
        Point2D ref = refToImage.applyInverse(imagePoint);   // image -> reference
        if (ref == null)
            return null;
        return refToTarget.apply(ref);                       // reference -> target
    }

    @Override
    public Optional<AffineTransform> asAffineTransform() {
        Optional<AffineTransform> a = refToImage.asAffineTransform();   // reference -> image
        Optional<AffineTransform> b = refToTarget.asAffineTransform();  // reference -> target
        if (a.isEmpty() || b.isEmpty())
            return Optional.empty();
        try {
            // target -> image = (reference -> image) . (target -> reference)
            AffineTransform result = new AffineTransform(a.get());
            result.concatenate(b.get().createInverse());
            return Optional.of(result);
        } catch (NoninvertibleTransformException e) {
            return Optional.empty();
        }
    }
}
