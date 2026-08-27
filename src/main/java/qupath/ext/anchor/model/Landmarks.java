package qupath.ext.anchor.model;

import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalInt;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Bridge between the landmark data model and QuPath {@code PathObject}s.
 * <p>
 * Each landmark is its own single-point annotation (per the project design): name {@code "LM-NN"}, a
 * per-object color keyed by id, and metadata (id, role, session info, timestamp, and - for grid
 * points - the original seed coordinates). All methods here are the sanctioned way to create and read
 * landmark objects, so the naming/metadata contract lives in one place.
 */
public final class Landmarks {

    private Landmarks() {}

    /** Distance (in image pixels) beyond which a grid point counts as having been dragged. */
    private static final double DRAG_EPSILON = 1e-6;

    /**
     * Create a single-point landmark annotation. Not added to any hierarchy - the caller adds it.
     *
     * @param landmarkId 1-based landmark id
     * @param x          x coordinate in full-resolution image pixels
     * @param y          y coordinate in full-resolution image pixels
     * @param role       provenance/purpose of the point
     * @param plane      image plane; {@code null} uses the default plane (z=0, t=0)
     * @param session    session context stamped into metadata; may be {@code null}
     */
    public static PathObject create(int landmarkId, double x, double y, LandmarkRole role,
                                    ImagePlane plane, SessionInfo session) {
        ImagePlane p = plane != null ? plane : ImagePlane.getDefaultPlane();
        ROI roi = ROIs.createPointsROI(x, y, p);
        PathObject object = PathObjects.createAnnotationObject(roi);
        object.setName(LandmarkKeys.nameForId(landmarkId));
        object.setColor(LandmarkPalette.colorForId(landmarkId));

        Map<String, String> m = object.getMetadata();
        m.put(LandmarkKeys.LANDMARK_ID, Integer.toString(landmarkId));
        m.put(LandmarkKeys.ROLE, role.key());
        if (session != null) {
            if (session.annotator() != null)
                m.put(LandmarkKeys.ANNOTATOR, session.annotator());
            if (session.mode() != null)
                m.put(LandmarkKeys.MODE, session.mode());
            if (session.sessionId() != null)
                m.put(LandmarkKeys.SESSION_ID, session.sessionId());
        }
        m.put(LandmarkKeys.CREATED_OR_MODIFIED, Instant.now().toString());
        if (role == LandmarkRole.GRID) {
            m.put(LandmarkKeys.SEED_X, Double.toString(x));
            m.put(LandmarkKeys.SEED_Y, Double.toString(y));
        }
        return object;
    }

    /** True if the object is one of our landmark annotations (by name prefix + id metadata). */
    public static boolean isLandmark(PathObject object) {
        return object != null
                && object.getName() != null
                && object.getName().startsWith(LandmarkKeys.NAME_PREFIX)
                && object.hasMetadata()
                && object.getMetadata().containsKey(LandmarkKeys.LANDMARK_ID);
    }

    /** The landmark id, or empty if absent/unparseable. */
    public static OptionalInt getId(PathObject object) {
        if (!isLandmark(object))
            return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(object.getMetadata().get(LandmarkKeys.LANDMARK_ID)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * The next landmark id to use for an image: one greater than the highest existing landmark id
     * among the given objects (1 if there are none). Lets new points continue the numbering instead
     * of colliding at 1 (e.g. grid points seeded after 3 alignment points start at 4).
     */
    public static int nextId(Collection<? extends PathObject> objects) {
        int max = 0;
        for (PathObject o : objects) {
            OptionalInt id = getId(o);
            if (id.isPresent())
                max = Math.max(max, id.getAsInt());
        }
        return max + 1;
    }

    /** The role, or {@code null} if not a landmark or unrecognized. */
    public static LandmarkRole getRole(PathObject object) {
        return isLandmark(object) ? LandmarkRole.fromKey(object.getMetadata().get(LandmarkKeys.ROLE)) : null;
    }

    /** The point location in full-resolution image pixels, or {@code null} if unavailable. */
    public static Point2D getPoint(PathObject object) {
        if (object == null)
            return null;
        ROI roi = object.getROI();
        if (roi == null || roi.getNumPoints() < 1)
            return null;
        Point2 pt = roi.getAllPoints().get(0);
        return new Point2D.Double(pt.getX(), pt.getY());
    }

    /**
     * True if this is a grid-seeded point whose current position differs from its seed position -
     * i.e. the user has dragged it to a refined location.
     */
    public static boolean isDragged(PathObject object) {
        if (getRole(object) != LandmarkRole.GRID)
            return false;
        Map<String, String> m = object.getMetadata();
        String sx = m.get(LandmarkKeys.SEED_X);
        String sy = m.get(LandmarkKeys.SEED_Y);
        Point2D current = getPoint(object);
        if (sx == null || sy == null || current == null)
            return false;
        try {
            double dx = current.getX() - Double.parseDouble(sx);
            double dy = current.getY() - Double.parseDouble(sy);
            return Math.hypot(dx, dy) > DRAG_EPSILON;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
