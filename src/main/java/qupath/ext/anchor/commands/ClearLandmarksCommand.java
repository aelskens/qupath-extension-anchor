package qupath.ext.anchor.commands;

import java.util.List;

import qupath.ext.anchor.model.Landmarks;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathObject;

/**
 * Remove every landmark (LM-*) annotation from the current image, leaving all non-landmark
 * annotations untouched. Enables a clean re-seed.
 */
public class ClearLandmarksCommand implements Runnable {

    private static final String TITLE = "Clear landmarks";

    private final QuPathGUI qupath;

    public ClearLandmarksCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        var viewer = qupath.getViewer();
        var imageData = viewer == null ? null : viewer.getImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage(TITLE, "Open an image first.");
            return;
        }

        var hierarchy = imageData.getHierarchy();
        List<PathObject> landmarks = hierarchy.getAnnotationObjects().stream()
                .filter(Landmarks::isLandmark)
                .toList();

        if (landmarks.isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No landmark (LM-*) annotations to remove.");
            return;
        }

        boolean confirm = Dialogs.showConfirmDialog(TITLE,
                "Remove " + landmarks.size() + " landmark annotation(s)? Other annotations are left untouched.");
        if (!confirm)
            return;

        hierarchy.removeObjects(landmarks, true);
        hierarchy.fireHierarchyChangedEvent(this);
        Dialogs.showInfoNotification(TITLE, "Removed " + landmarks.size() + " landmark(s).");
    }
}
