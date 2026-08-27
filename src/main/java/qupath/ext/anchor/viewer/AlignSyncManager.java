package qupath.ext.anchor.viewer;

import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Owns the single active {@link AlignedSyncController} and the user's chosen reference viewer, so
 * they persist across commands and the panel. Starting a new aligned sync stops any previous one.
 * Also carries a short human-readable status string (e.g. last fit quality) for the panel.
 */
public final class AlignSyncManager {

    private static final AlignSyncManager INSTANCE = new AlignSyncManager();

    private AlignedSyncController active;
    private QuPathViewer referenceViewer;
    private String lastStatus = "Not aligned.";

    private AlignSyncManager() {}

    public static AlignSyncManager getInstance() {
        return INSTANCE;
    }

    /** Stop any active controller, then start and retain the given one. */
    public synchronized void start(AlignedSyncController controller) {
        stop();
        active = controller;
        controller.start();
    }

    /** Stop and clear the active controller, if any. The reference viewer choice is kept. */
    public synchronized void stop() {
        if (active != null) {
            active.stop();
            active = null;
        }
    }

    public synchronized boolean isActive() {
        return active != null;
    }

    public synchronized AlignedSyncController getActiveController() {
        return active;
    }

    /** The viewer the user chose to hold the reference image (may be {@code null} = not chosen yet). */
    public synchronized QuPathViewer getReferenceViewer() {
        return referenceViewer;
    }

    public synchronized void setReferenceViewer(QuPathViewer viewer) {
        this.referenceViewer = viewer;
    }

    public synchronized String getLastStatus() {
        return lastStatus;
    }

    public synchronized void setLastStatus(String status) {
        this.lastStatus = status;
    }
}
