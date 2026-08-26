package qupath.ext.anchor.transform;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-fit validation: minimum point counts and degeneracy (collinearity / coincidence) checks.
 * Runs before the numeric solver so failures produce specific, user-facing messages rather than a
 * generic "singular matrix".
 */
public final class TransformValidation {

    private TransformValidation() {}

    /** Relative epsilon (against the point-cloud bounding-box diagonal) for degeneracy tests. */
    private static final double RELATIVE_EPSILON = 1e-6;

    public static ValidationResult validate(TransformType type, List<Point2D> source, List<Point2D> target) {
        if (type == null)
            return ValidationResult.fail("Transform type is required.");
        if (source == null || target == null)
            return ValidationResult.fail("Source and target point lists are required.");
        if (source.size() != target.size())
            return ValidationResult.fail(String.format(
                    "Source and target must have the same number of points (got %d and %d).",
                    source.size(), target.size()));

        int n = source.size();
        if (n < type.hardMinimumPoints())
            return ValidationResult.fail(String.format(
                    "%s needs at least %d matched points, got %d.",
                    type, type.hardMinimumPoints(), n));

        List<String> warnings = new ArrayList<>();
        if (n < type.recommendedMinimumPoints())
            warnings.add(String.format(
                    "%s is recommended with at least %d points; %d given - the fit may be poorly constrained.",
                    type, type.recommendedMinimumPoints(), n));

        switch (type) {
            case AFFINE, TPS -> {
                if (effectivelyCollinear(source))
                    return ValidationResult.fail("Source points are collinear; an " + type
                            + " fit is underdetermined. Spread the points out.");
                if (effectivelyCollinear(target))
                    return ValidationResult.fail("Target points are collinear; an " + type
                            + " fit is underdetermined. Spread the points out.");
            }
            case RIGID, SIMILARITY -> {
                if (allCoincident(source))
                    return ValidationResult.fail("Source points are coincident; cannot fit " + type + ".");
                if (allCoincident(target))
                    return ValidationResult.fail("Target points are coincident; cannot fit " + type + ".");
            }
        }
        return ValidationResult.ok(warnings);
    }

    /** True if all points lie (within a scale-relative epsilon) on a single line, or all coincide. */
    static boolean effectivelyCollinear(List<Point2D> points) {
        int n = points.size();
        if (n < 3)
            return true; // 2 or fewer points never define a plane
        double diag = boundingBoxDiagonal(points);
        if (diag <= 0)
            return true; // all coincident
        double tol = RELATIVE_EPSILON * diag;

        // Anchor on the first point and the farthest point from it to define a robust line direction.
        Point2D p0 = points.get(0);
        int farIdx = -1;
        double farDist = -1;
        for (int i = 1; i < n; i++) {
            double dd = points.get(i).distance(p0);
            if (dd > farDist) {
                farDist = dd;
                farIdx = i;
            }
        }
        if (farDist <= tol)
            return true; // all coincident with p0

        Point2D p1 = points.get(farIdx);
        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();
        double len = Math.hypot(dx, dy);

        // Max perpendicular distance of any point from the p0->p1 line.
        for (Point2D p : points) {
            double cross = Math.abs((p.getX() - p0.getX()) * dy - (p.getY() - p0.getY()) * dx) / len;
            if (cross > tol)
                return false;
        }
        return true;
    }

    static boolean allCoincident(List<Point2D> points) {
        return boundingBoxDiagonal(points) <= RELATIVE_EPSILON;
    }

    private static double boundingBoxDiagonal(List<Point2D> points) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Point2D p : points) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }
        return Math.hypot(maxX - minX, maxY - minY);
    }
}
