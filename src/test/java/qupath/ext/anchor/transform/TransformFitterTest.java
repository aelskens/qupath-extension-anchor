package qupath.ext.anchor.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;

import org.junit.jupiter.api.Test;

class TransformFitterTest {

    private static Point2D pt(double x, double y) {
        return new Point2D.Double(x, y);
    }

    @Test
    void affineIsExactForThreeNonCollinearPoints() {
        // Known affine: x' = 2x + 0.5y + 3 ; y' = -0.1x + 1.5y - 2
        AffineTransform truth = new AffineTransform(2.0, -0.1, 0.5, 1.5, 3.0, -2.0);
        List<Point2D> src = List.of(pt(0, 0), pt(10, 0), pt(0, 10));
        List<Point2D> tgt = src.stream().map(p -> truth.transform(p, null)).toList();

        TransformFitResult result = TransformFitter.fit(TransformType.AFFINE, src, tgt);
        assertTrue(result.rmsErrorPixels() < 1e-6, "exact fit should have ~0 residual");

        // A fresh test point should map the same as the ground-truth transform.
        Point2D test = pt(7, 4);
        Point2D expected = truth.transform(test, null);
        Point2D actual = result.transform().apply(test);
        assertEquals(expected.getX(), actual.getX(), 1e-6);
        assertEquals(expected.getY(), actual.getY(), 1e-6);
    }

    @Test
    void similarityRecoversRotationScaleTranslation() {
        // 90-degree rotation, scale 2, translation (5, -3).
        double s = 2.0, theta = Math.PI / 2;
        AffineTransform truth = new AffineTransform(
                s * Math.cos(theta), s * Math.sin(theta),
                -s * Math.sin(theta), s * Math.cos(theta), 5, -3);
        List<Point2D> src = List.of(pt(1, 1), pt(4, 2), pt(2, 5), pt(6, 6));
        List<Point2D> tgt = src.stream().map(p -> truth.transform(p, null)).toList();

        TransformFitResult result = TransformFitter.fit(TransformType.SIMILARITY, src, tgt);
        assertTrue(result.rmsErrorPixels() < 1e-6, "similarity fit of a pure similarity should be exact");
    }

    @Test
    void collinearAffineIsRejected() {
        List<Point2D> src = List.of(pt(0, 0), pt(1, 1), pt(2, 2));
        List<Point2D> tgt = List.of(pt(0, 0), pt(1, 0), pt(2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TransformFitter.fit(TransformType.AFFINE, src, tgt));
    }

    @Test
    void tooFewPointsIsRejected() {
        List<Point2D> src = List.of(pt(0, 0), pt(1, 0));
        List<Point2D> tgt = List.of(pt(0, 0), pt(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TransformFitter.fit(TransformType.AFFINE, src, tgt));
    }

    @Test
    void tpsInterpolatesControlPointsAndReproducesAffine() {
        // TPS fitted to affine-transformed data should interpolate the control points exactly and,
        // since an affine lies in the TPS null space, reproduce the affine away from them too.
        AffineTransform truth = new AffineTransform(1.2, 0.1, -0.05, 0.9, 30, -10);
        List<Point2D> src = List.of(pt(0, 0), pt(100, 0), pt(0, 100), pt(100, 100), pt(50, 50));
        List<Point2D> tgt = src.stream().map(p -> truth.transform(p, null)).toList();

        TransformFitResult result = TransformFitter.fit(TransformType.TPS, src, tgt);
        assertTrue(result.rmsErrorPixels() < 1e-6, "TPS interpolates its control points");

        Point2D test = pt(25, 75);
        Point2D expected = truth.transform(test, null);
        Point2D actual = result.transform().apply(test);
        assertEquals(expected.getX(), actual.getX(), 1e-3);
        assertEquals(expected.getY(), actual.getY(), 1e-3);
    }

    @Test
    void tpsRejectsTooFewPoints() {
        List<Point2D> src = List.of(pt(0, 0), pt(1, 0));
        List<Point2D> tgt = List.of(pt(0, 0), pt(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TransformFitter.fit(TransformType.TPS, src, tgt));
    }

    @Test
    void correspondenceResolvesByIdNotOrder() {
        List<IdPoint> source = List.of(
                new IdPoint(3, pt(30, 30)), new IdPoint(1, pt(10, 10)), new IdPoint(2, pt(20, 20)));
        List<IdPoint> target = List.of(
                new IdPoint(1, pt(11, 11)), new IdPoint(2, pt(21, 21)), new IdPoint(3, pt(31, 31)));
        CorrespondenceSet cs = CorrespondenceSet.resolve(source, target);
        assertEquals(List.of(1, 2, 3), cs.landmarkIds());
        assertEquals(pt(10, 10), cs.source().get(0));
        assertEquals(pt(11, 11), cs.target().get(0));
    }
}
