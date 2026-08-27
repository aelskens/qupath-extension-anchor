package qupath.ext.anchor.transform.internal;

import java.awt.geom.Point2D;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import qupath.ext.anchor.transform.AffineLinearTransform;
import qupath.ext.anchor.transform.TransformType;

/**
 * Least-squares affine fit: solves {@code target = A*source + t} for the 6 parameters via QR
 * decomposition of the shared design matrix (exact when exactly 3 non-collinear points are given).
 */
public final class AffineFitter {

    private AffineFitter() {}

    public static AffineLinearTransform fit(List<Point2D> source, List<Point2D> target) {
        int n = source.size();
        RealMatrix design = new Array2DRowRealMatrix(n, 3);
        RealVector rhsX = new ArrayRealVector(n);
        RealVector rhsY = new ArrayRealVector(n);
        for (int i = 0; i < n; i++) {
            Point2D s = source.get(i);
            design.setEntry(i, 0, s.getX());
            design.setEntry(i, 1, s.getY());
            design.setEntry(i, 2, 1.0);
            rhsX.setEntry(i, target.get(i).getX());
            rhsY.setEntry(i, target.get(i).getY());
        }

        DecompositionSolver solver = new QRDecomposition(design).getSolver();
        if (!solver.isNonSingular())
            throw new IllegalArgumentException(
                    "Degenerate point configuration for affine fit (points collinear or duplicated).");

        RealVector px = solver.solve(rhsX); // [a, b, tx]
        RealVector py = solver.solve(rhsY); // [c, d, ty]
        return new AffineLinearTransform(TransformType.AFFINE,
                px.getEntry(0), px.getEntry(1),
                py.getEntry(0), py.getEntry(1),
                px.getEntry(2), py.getEntry(2));
    }
}
