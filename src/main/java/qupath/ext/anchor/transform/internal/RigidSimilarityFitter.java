package qupath.ext.anchor.transform.internal;

import java.awt.geom.Point2D;
import java.util.List;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import qupath.ext.anchor.transform.AffineLinearTransform;
import qupath.ext.anchor.transform.TransformType;

/**
 * Closed-form rigid / similarity fit (Umeyama / Kabsch): estimates rotation (+ uniform scale for
 * SIMILARITY) + translation from matched points via the SVD of the 2x2 cross-covariance, with a
 * reflection guard so the result is always a proper rotation.
 * <p>
 * Assumes the images are not mirrored relative to each other (true for serial sections / IF panels /
 * different stains of the same tissue).
 */
public final class RigidSimilarityFitter {

    private RigidSimilarityFitter() {}

    private static final double EPSILON = 1e-12;

    public static AffineLinearTransform fit(TransformType type, List<Point2D> source, List<Point2D> target) {
        if (type != TransformType.RIGID && type != TransformType.SIMILARITY)
            throw new IllegalArgumentException("RigidSimilarityFitter handles RIGID/SIMILARITY, got " + type);

        int n = source.size();
        double muSx = 0, muSy = 0, muTx = 0, muTy = 0;
        for (int i = 0; i < n; i++) {
            muSx += source.get(i).getX();
            muSy += source.get(i).getY();
            muTx += target.get(i).getX();
            muTy += target.get(i).getY();
        }
        muSx /= n; muSy /= n; muTx /= n; muTy /= n;

        // Cross-covariance H = sum (source_centered)(target_centered)^T, and source variance.
        double h00 = 0, h01 = 0, h10 = 0, h11 = 0, varSource = 0;
        for (int i = 0; i < n; i++) {
            double px = source.get(i).getX() - muSx;
            double py = source.get(i).getY() - muSy;
            double qx = target.get(i).getX() - muTx;
            double qy = target.get(i).getY() - muTy;
            h00 += px * qx; h01 += px * qy;
            h10 += py * qx; h11 += py * qy;
            varSource += px * px + py * py;
        }
        if (varSource < EPSILON)
            throw new IllegalArgumentException("Source points are coincident; cannot fit " + type + ".");

        RealMatrix h = MatrixUtils.createRealMatrix(new double[][]{{h00, h01}, {h10, h11}});
        SingularValueDecomposition svd = new SingularValueDecomposition(h);
        RealMatrix u = svd.getU();
        RealMatrix v = svd.getV();
        double[] sigma = svd.getSingularValues(); // descending

        // Reflection guard: force det(R) = +1.
        RealMatrix probe = v.multiply(u.transpose());
        double det = probe.getEntry(0, 0) * probe.getEntry(1, 1) - probe.getEntry(0, 1) * probe.getEntry(1, 0);
        double dSign = det < 0 ? -1.0 : 1.0;

        RealMatrix dMatrix = MatrixUtils.createRealDiagonalMatrix(new double[]{1.0, dSign});
        RealMatrix r = v.multiply(dMatrix).multiply(u.transpose());

        double scale = type == TransformType.SIMILARITY
                ? (sigma[0] + dSign * sigma[1]) / varSource
                : 1.0;

        double a = scale * r.getEntry(0, 0);
        double b = scale * r.getEntry(0, 1);
        double c = scale * r.getEntry(1, 0);
        double d = scale * r.getEntry(1, 1);
        double tx = muTx - (a * muSx + b * muSy);
        double ty = muTy - (c * muSx + d * muSy);
        return new AffineLinearTransform(type, a, b, c, d, tx, ty);
    }
}
