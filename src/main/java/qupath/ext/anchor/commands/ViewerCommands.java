package qupath.ext.anchor.commands;

import qupath.ext.anchor.overlay.ColorProjectionManager;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.ext.anchor.viewer.AlignedSyncController;
import qupath.ext.anchor.viewer.ReferenceIndicator;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Small viewer-management commands: reset views, toggle QuPath's built-in synchronization, and stop
 * the custom aligned sync.
 */
public final class ViewerCommands {

    private static final String TITLE = "Anchor";

    private ViewerCommands() {}

    /**
     * Restore every open viewer to its original state: no rotation, and zoom-to-fit (which resets
     * scale and translation). After stopping aligned sync, this returns the images to how they looked
     * before any alignment was applied. While aligned sync is active, resetting the reference simply
     * re-fits and propagates to the synced viewers.
     */
    public static void resetViews(QuPathGUI qupath) {
        AlignedSyncController controller = AlignSyncManager.getInstance().getActiveController();
        if (controller != null) {
            // While synced, reset the reference and let it propagate, so the anchor stays put and
            // does not flip between viewers on repeated resets.
            controller.fitReferenceAndSync();
            Dialogs.showInfoNotification(TITLE, "Reset to the reference view (kept in sync).");
            return;
        }
        int count = 0;
        for (QuPathViewer v : qupath.getAllViewers()) {
            if (v.getImageData() != null) {
                v.setRotation(0);
                v.zoomToFit();
                count++;
            }
        }
        Dialogs.showInfoNotification(TITLE, "Reset " + count + " viewer(s) to fit (rotation cleared).");
    }

    /** Mark the current (active) viewer as the reference image for alignment. */
    public static void setReferenceToCurrent(QuPathGUI qupath) {
        QuPathViewer v = qupath.getViewer();
        if (v == null || v.getImageData() == null) {
            Dialogs.showErrorMessage(TITLE, "Select a viewer with an image first.");
            return;
        }
        ReferenceIndicator.setReference(qupath, v);
        Dialogs.showInfoNotification(TITLE, "Reference set to the current viewer.");
    }

    /** Stop the custom aligned view synchronization; viewers keep their current positions. */
    public static void stopAlignedSync(QuPathGUI qupath) {
        boolean wasActive = AlignSyncManager.getInstance().isActive();
        AlignSyncManager.getInstance().stop();
        ColorProjectionManager.getInstance().onAlignmentChanged(qupath);
        Dialogs.showInfoNotification(TITLE, wasActive ? "Aligned sync stopped." : "Aligned sync was not active.");
    }
}
