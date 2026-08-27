package qupath.ext.anchor.session;

import java.time.Instant;

import qupath.ext.anchor.model.SessionInfo;
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

    private AnchorSession() {}

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
