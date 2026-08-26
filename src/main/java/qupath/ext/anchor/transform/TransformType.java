package qupath.ext.anchor.transform;

/**
 * Supported landmark transform types, each with a hard minimum point count (below which a fit is
 * refused as underdetermined) and a recommended minimum (below which a fit is allowed but warned).
 *
 * <ul>
 *   <li>{@link #RIGID} - rotation + translation (2 points).</li>
 *   <li>{@link #SIMILARITY} - rigid + uniform scale (2 points).</li>
 *   <li>{@link #AFFINE} - linear + translation (3 non-collinear points); exact at 3.</li>
 *   <li>{@link #TPS} - thin-plate spline, nonlinear (3 non-collinear minimum, 5+ recommended).</li>
 * </ul>
 */
public enum TransformType {
    RIGID(2, 2),
    SIMILARITY(2, 2),
    AFFINE(3, 3),
    TPS(3, 5);

    private final int hardMinimumPoints;
    private final int recommendedMinimumPoints;

    TransformType(int hardMinimumPoints, int recommendedMinimumPoints) {
        this.hardMinimumPoints = hardMinimumPoints;
        this.recommendedMinimumPoints = recommendedMinimumPoints;
    }

    public int hardMinimumPoints() {
        return hardMinimumPoints;
    }

    public int recommendedMinimumPoints() {
        return recommendedMinimumPoints;
    }

    /** True for the linear family (RIGID/SIMILARITY/AFFINE), which have a single-matrix representation. */
    public boolean isLinear() {
        return this != TPS;
    }
}
