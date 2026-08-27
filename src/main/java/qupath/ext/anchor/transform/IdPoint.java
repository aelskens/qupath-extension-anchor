package qupath.ext.anchor.transform;

import java.awt.geom.Point2D;

/**
 * A landmark point tagged with its landmark id, used to build id-matched correspondences between two
 * images without relying on list order.
 *
 * @param landmarkId the landmark id
 * @param point      the location in image pixels
 */
public record IdPoint(int landmarkId, Point2D point) {

    public IdPoint {
        if (point == null)
            throw new IllegalArgumentException("point must not be null");
    }
}
