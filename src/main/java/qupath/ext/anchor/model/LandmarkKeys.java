package qupath.ext.anchor.model;

import java.util.Locale;

/**
 * Metadata key names and naming conventions for landmark {@code PathObject}s.
 * <p>
 * Metadata is stored in QuPath's per-object {@code Map<String,String>} (0.6+ API); every value is a
 * string, so numeric values are serialized/parsed explicitly.
 */
public final class LandmarkKeys {

    private LandmarkKeys() {}

    /** Prefix for the annotation name, e.g. {@code "LM-07"}. */
    public static final String NAME_PREFIX = "LM-";

    public static final String LANDMARK_ID = "landmarkId";
    public static final String ROLE = "role";
    public static final String ANNOTATOR = "annotator";
    public static final String MODE = "mode";
    public static final String SESSION_ID = "sessionId";
    public static final String CREATED_OR_MODIFIED = "createdOrModified";

    /** Original seeded coordinates, stored only for {@link LandmarkRole#GRID} points so a drag can be detected. */
    public static final String SEED_X = "seedX";
    public static final String SEED_Y = "seedY";

    /** Canonical annotation name for a landmark id, e.g. {@code nameForId(7) == "LM-07"}. */
    public static String nameForId(int landmarkId) {
        return String.format(Locale.ROOT, "%s%02d", NAME_PREFIX, landmarkId);
    }
}
