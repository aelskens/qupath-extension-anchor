package qupath.ext.anchor.session;

import java.time.Instant;

import qupath.ext.anchor.model.PointSource;
import qupath.ext.anchor.model.SessionInfo;
import qupath.ext.anchor.transform.TransformType;
import qupath.fx.dialogs.Dialogs;

/**
 * Process-wide annotation session state: the annotator id, mode, and a generated session id.
 * <p>
 * The annotator is asked for once (on the first landmark placed) and then reused for the rest of the
 * session, so the user isn't re-prompted on every action. Use {@link #reset()} to start a new session
 * (e.g. a different annotator).
 */
public final class AnchorSession {

    private static final AnchorSession INSTANCE = new AnchorSession();

    private String annotator;
    private String sessionId;
    private String mode = SessionInfo.MODE_ASSISTED;
    // Alignment choices made in the panel, shared with the menu/keyboard shortcut so both run the same
    // fit (the shortcut used to hardcode affine + manual points).
    private TransformType overlayTransformType = TransformType.AFFINE;
    private PointSource pointSource = PointSource.MANUAL;

    private AnchorSession() {}

    /** The transform used for the overlay (the viewer sync itself is always a similarity). */
    public synchronized TransformType overlayTransformType() {
        return overlayTransformType;
    }

    public synchronized void setOverlayTransformType(TransformType type) {
        if (type != null)
            this.overlayTransformType = type;
    }

    /** Which landmarks feed the alignment fit. */
    public synchronized PointSource pointSource() {
        return pointSource;
    }

    public synchronized void setPointSource(PointSource source) {
        if (source != null)
            this.pointSource = source;
    }

    public static AnchorSession getInstance() {
        return INSTANCE;
    }

    public synchronized boolean hasAnnotator() {
        return annotator != null && !annotator.isBlank();
    }

    public synchronized String annotator() {
        return annotator;
    }

    public synchronized void setAnnotator(String annotator) {
        this.annotator = annotator == null || annotator.isBlank() ? null : annotator.trim();
    }

    public synchronized String mode() {
        return mode;
    }

    public synchronized void setMode(String mode) {
        if (mode != null && !mode.isBlank())
            this.mode = mode;
    }

    public synchronized String sessionId() {
        if (sessionId == null)
            sessionId = "session-" + Instant.now();
        return sessionId;
    }

    /**
     * Return the current {@link SessionInfo}, prompting once for the annotator id if it hasn't been
     * set yet. Returns {@code null} if the user cancels the prompt.
     */
    public synchronized SessionInfo ensureSessionInfo(String dialogTitle) {
        if (!hasAnnotator()) {
            String input = Dialogs.showInputDialog(dialogTitle,
                    "Annotator id (reused for the rest of this session):", "annotator-1");
            if (input == null)
                return null;
            setAnnotator(input);
            if (!hasAnnotator())
                return null;
        }
        return new SessionInfo(annotator, mode, sessionId());
    }

    /** Clear the session so the next action re-prompts for the annotator and starts a new session id. */
    public synchronized void reset() {
        annotator = null;
        sessionId = null;
        mode = SessionInfo.MODE_ASSISTED;
    }
}
