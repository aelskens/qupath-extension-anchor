package qupath.ext.anchor.viewer;

import qupath.ext.anchor.overlay.ReferenceOverlay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;

/**
 * Marks the reference viewer both in state ({@link AlignSyncManager}) and visually (a "REFERENCE"
 * badge overlay on that viewer only).
 */
public final class ReferenceIndicator {

    private ReferenceIndicator() {}

    /** Set the reference viewer and refresh the on-viewer badge across all viewers. */
    public static void setReference(QuPathGUI qupath, QuPathViewer viewer) {
        AlignSyncManager.getInstance().setReferenceViewer(viewer);
        refresh(qupath);
    }

    /** Re-apply the badge so exactly the current reference viewer shows it. */
    public static void refresh(QuPathGUI qupath) {
        QuPathViewer reference = AlignSyncManager.getInstance().getReferenceViewer();
        for (QuPathViewer v : qupath.getAllViewers()) {
            v.getCustomOverlayLayers().removeIf(o -> o instanceof ReferenceOverlay);
            if (v == reference && v.getImageData() != null) {
                PathOverlay badge = new ReferenceOverlay(v);
                v.getCustomOverlayLayers().add(badge);
            }
        }
    }

    /** Remove the badge from every viewer. */
    public static void clear(QuPathGUI qupath) {
        for (QuPathViewer v : qupath.getAllViewers())
            v.getCustomOverlayLayers().removeIf(o -> o instanceof ReferenceOverlay);
    }
}
