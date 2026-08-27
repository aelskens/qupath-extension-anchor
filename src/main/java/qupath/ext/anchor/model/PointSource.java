package qupath.ext.anchor.model;

import qupath.lib.objects.PathObject;

/**
 * Which landmarks feed a transform fit. Lets the same fitter run on different subsets of a viewer's
 * landmarks (the manually-placed points, all landmarks, or grid points that have been dragged from
 * their seed).
 */
public enum PointSource {

    MANUAL("Manual points"),
    ALL("All landmarks"),
    DRAGGED_GRID("Dragged grid points");

    private final String label;

    PointSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True if the given object should be included under this source. */
    public boolean includes(PathObject object) {
        if (!Landmarks.isLandmark(object))
            return false;
        LandmarkRole role = Landmarks.getRole(object);
        return switch (this) {
            case MANUAL -> role == LandmarkRole.MANUAL;
            case ALL -> true;
            case DRAGGED_GRID -> role == LandmarkRole.GRID && Landmarks.isDragged(object);
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
