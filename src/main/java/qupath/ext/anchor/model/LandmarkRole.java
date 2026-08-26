package qupath.ext.anchor.model;

import java.util.Locale;

/**
 * Provenance / purpose of a landmark point, persisted under {@link LandmarkKeys#ROLE}.
 * <p>
 * Used to filter which points feed a transform fit:
 * <ul>
 *   <li>{@link #GRID} - seeded from the regular grid. A grid point that has since been moved from its
 *       seeded position is considered "dragged" (see {@code Landmarks.isDragged}).</li>
 *   <li>{@link #MANUAL} - a landmark placed by hand (from "Place alignment points" or "Add
 *       landmark"), not on the grid. These are the points that drive alignment by default.</li>
 * </ul>
 */
public enum LandmarkRole {
    GRID,
    MANUAL;

    /** Lower-case string form stored in metadata. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parse a stored role key; returns {@code null} if unrecognized or {@code null}. */
    public static LandmarkRole fromKey(String key) {
        if (key == null)
            return null;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (LandmarkRole role : values()) {
            if (role.key().equals(normalized))
                return role;
        }
        return null;
    }
}
