package qupath.ext.anchor.seeding;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic "most-square" grid layout for landmark seeding.
 * <p>
 * Given N and an image's dimensions, lays out points in the most-square grid that fits N
 * (rows x cols with cols &gt;= rows), evenly spaced and inset from the edges by a margin. The result is
 * a pure function of {@code (n, width, height, marginFrac)} - no randomness, no clock - so re-seeding
 * the same image reproduces identical coordinates.
 */
public final class GridSeeder {

    private GridSeeder() {}

    public static final double DEFAULT_MARGIN_FRACTION = 0.10;

    /** Seed positions in full-resolution image pixels, in row-major order, ids 1..N. */
    public static List<Point2D> seed(int n, double width, double height, double marginFraction) {
        if (n < 1)
            throw new IllegalArgumentException("N must be >= 1, got " + n);
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Image dimensions must be positive, got " + width + "x" + height);
        if (marginFraction < 0 || marginFraction >= 0.5)
            throw new IllegalArgumentException("marginFraction must be in [0, 0.5), got " + marginFraction);

        int cols = columns(n);
        int rows = rows(n);

        double left = width * marginFraction;
        double top = height * marginFraction;
        double usableWidth = width * (1.0 - 2.0 * marginFraction);
        double usableHeight = height * (1.0 - 2.0 * marginFraction);

        // Even spacing; a single row/column is centered on that axis to avoid a divide-by-zero.
        double dx = cols > 1 ? usableWidth / (cols - 1) : 0.0;
        double dy = rows > 1 ? usableHeight / (rows - 1) : 0.0;
        double x0 = cols > 1 ? left : width / 2.0;
        double y0 = rows > 1 ? top : height / 2.0;

        List<Point2D> points = new ArrayList<>(n);
        int placed = 0;
        for (int r = 0; r < rows && placed < n; r++) {
            for (int c = 0; c < cols && placed < n; c++) {
                points.add(new Point2D.Double(x0 + c * dx, y0 + r * dy));
                placed++;
            }
        }
        return points;
    }

    /** Number of columns in the most-square layout for N. */
    public static int columns(int n) {
        return (int) Math.ceil(Math.sqrt(n));
    }

    /** Number of rows in the most-square layout for N. */
    public static int rows(int n) {
        return (int) Math.ceil(n / (double) columns(n));
    }
}
