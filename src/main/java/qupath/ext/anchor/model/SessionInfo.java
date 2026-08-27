package qupath.ext.anchor.model;

/**
 * Per-session annotation context stamped onto each landmark's metadata.
 *
 * @param annotator identifier of the person annotating
 * @param mode      annotation mode, {@code "assisted"} or {@code "blind"}
 * @param sessionId identifier for this annotation session
 */
public record SessionInfo(String annotator, String mode, String sessionId) {

    public static final String MODE_ASSISTED = "assisted";
    public static final String MODE_BLIND = "blind";
}
