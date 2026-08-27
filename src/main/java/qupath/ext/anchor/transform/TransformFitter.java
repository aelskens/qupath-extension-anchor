package qupath.ext.anchor.transform;

import java.awt.geom.Point2D;
import java.util.List;

import qupath.ext.anchor.transform.internal.AffineFitter;
import qupath.ext.anchor.transform.internal.RigidSimilarityFitter;

/**
 * Fits a {@link LandmarkTransform} from matched correspondences.
 * <p>
 * The recommended entry point is {@link #fit(TransformType, CorrespondenceSet)}, which validates,
 * dispatches to the appropriate numeric fitter, and returns a {@link TransformFitResult} with quality
 * metrics. The list-based overload exists for pure numeric tests.
 */
public final class TransformFitter {

    private TransformFitter() {}

    public static TransformFitResult fit(TransformType type, CorrespondenceSet correspondences) {
        return fit(type, correspondences.landmarkIds(), correspondences.source(), correspondences.target());
    }

    /** Fit from raw ordered lists (matched by index). Prefer the {@link CorrespondenceSet} overload. */
    public static TransformFitResult fit(TransformType type, List<Point2D> source, List<Point2D> target) {
        return fit(type, null, source, target);
    }

    private static TransformFitResult fit(TransformType type, List<Integer> ids,
                                          List<Point2D> source, List<Point2D> target) {
        ValidationResult validation = TransformValidation.validate(type, source, target);
        if (!validation.valid())
            throw new IllegalArgumentException(validation.error());

        LandmarkTransform transform = switch (type) {
            case AFFINE -> AffineFitter.fit(source, target);
            case RIGID, SIMILARITY -> RigidSimilarityFitter.fit(type, source, target);
            case TPS -> ImglibTpsTransform.fit(source, target);
        };

        return TransformFitResult.compute(type, transform, ids, source, target, validation.warnings());
    }
}
