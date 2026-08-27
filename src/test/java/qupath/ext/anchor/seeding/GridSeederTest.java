package qupath.ext.anchor.seeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;

import org.junit.jupiter.api.Test;

class GridSeederTest {

    private static final double W = 1000;
    private static final double H = 800;
    private static final double MARGIN = 0.10;

    @Test
    void seedCountMatchesN() {
        for (int n : new int[]{1, 2, 5, 9, 16, 17}) {
            assertEquals(n, GridSeeder.seed(n, W, H, MARGIN).size(), "N=" + n);
        }
    }

    @Test
    void deterministicForSameInputs() {
        List<Point2D> a = GridSeeder.seed(9, W, H, MARGIN);
        List<Point2D> b = GridSeeder.seed(9, W, H, MARGIN);
        assertEquals(a, b);
    }

    @Test
    void mostSquareLayout() {
        assertEquals(3, GridSeeder.columns(9));
        assertEquals(3, GridSeeder.rows(9));
        assertEquals(4, GridSeeder.columns(10));
        assertEquals(3, GridSeeder.rows(10));
    }

    @Test
    void singlePointIsCentered() {
        Point2D p = GridSeeder.seed(1, W, H, MARGIN).get(0);
        assertEquals(W / 2.0, p.getX(), 1e-9);
        assertEquals(H / 2.0, p.getY(), 1e-9);
    }

    @Test
    void allPointsWithinMargins() {
        for (Point2D p : GridSeeder.seed(16, W, H, MARGIN)) {
            assertTrue(p.getX() >= W * MARGIN - 1e-6 && p.getX() <= W * (1 - MARGIN) + 1e-6);
            assertTrue(p.getY() >= H * MARGIN - 1e-6 && p.getY() <= H * (1 - MARGIN) + 1e-6);
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> GridSeeder.seed(0, W, H, MARGIN));
        assertThrows(IllegalArgumentException.class, () -> GridSeeder.seed(4, 0, H, MARGIN));
        assertThrows(IllegalArgumentException.class, () -> GridSeeder.seed(4, W, H, 0.5));
    }
}
