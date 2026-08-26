package qupath.ext.anchor.transform;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fitted transform plus its quality metrics, so the UI can show fit quality and flag the worst
 * landmark. Residuals are {@code target - transform.apply(source)} in the input coordinate units
 * (pixels); conversion to physical units is a reporting-layer concern.
 *
 * @param type          the fitted transform type
 * @param transform     the fitted transform
 * @param landmarkIds   matched ids (ascending); empty if the fit was built from raw ordered lists
 * @param residuals     residual vectors, aligned with {@code landmarkIds} / input order
 * @param rmsErrorPixels root-mean-square residual magnitude
 * @param maxErrorPixels largest residual magnitude
 * @param worstLandmarkId id with the largest residual, or -1 if ids were not supplied
 * @param warnings      non-fatal cautions carried from validation
 */
public record TransformFitResult(
        TransformType type,
        LandmarkTransform transform,
        List<Integer> landmarkIds,
        List<Point2D> residuals,
        double rmsErrorPixels,
        double maxErrorPixels,
        int worstLandmarkId,
        List<String> warnings) {

    public TransformFitResult {
        landmarkIds = landmarkIds == null ? List.of() : List.copyOf(landmarkIds);
        residuals = residuals == null ? List.of() : List.copyOf(residuals);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Compute residuals + RMS/max error for a fitted transform over the given correspondences. */
    static TransformFitResult compute(TransformType type, LandmarkTransform transform,
                                      List<Integer> ids, List<Point2D> source, List<Point2D> target,
                                      List<String> warnings) {
        int n = source.size();
        List<Point2D> residuals = new ArrayList<>(n);
        double sumSq = 0;
        double maxSq = -1;
        int worstIndex = -1;
        for (int i = 0; i < n; i++) {
            Point2D predicted = transform.apply(source.get(i));
            double dx = target.get(i).getX() - predicted.getX();
            double dy = target.get(i).getY() - predicted.getY();
            residuals.add(new Point2D.Double(dx, dy));
            double sq = dx * dx + dy * dy;
            sumSq += sq;
            if (sq > maxSq) {
                maxSq = sq;
                worstIndex = i;
            }
        }
        double rms = n > 0 ? Math.sqrt(sumSq / n) : 0.0;
        double max = maxSq >= 0 ? Math.sqrt(maxSq) : 0.0;
        int worstId = (ids != null && !ids.isEmpty() && worstIndex >= 0) ? ids.get(worstIndex) : -1;
        List<Integer> idList = ids == null ? Collections.emptyList() : ids;
        return new TransformFitResult(type, transform, idList, residuals, rms, max, worstId, warnings);
    }
}
