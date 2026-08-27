package qupath.ext.anchor.commands;

import java.util.List;

import qupath.ext.anchor.model.Landmarks;
import qupath.ext.anchor.model.LandmarkRole;
import qupath.ext.anchor.model.SessionInfo;
import qupath.ext.anchor.session.AnchorSession;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;

/**
 * Add a single landmark (role {@link LandmarkRole#MANUAL}) at the center of the current view, with
 * the next available id. The user then drags it onto the feature of interest.
 */
public class AddLandmarkCommand implements Runnable {

    private static final String TITLE = "Add landmark";

    private final QuPathGUI qupath;

    public AddLandmarkCommand(QuPathGUI qupath) {
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

        SessionInfo session = AnchorSession.getInstance().ensureSessionInfo(TITLE);
        if (session == null)
            return;

        var hierarchy = imageData.getHierarchy();
        int id = Landmarks.nextId(hierarchy.getAnnotationObjects());
        double x = viewer.getCenterPixelX();
        double y = viewer.getCenterPixelY();

        PathObject landmark = Landmarks.create(id, x, y, LandmarkRole.MANUAL, ImagePlane.getDefaultPlane(), session);
        hierarchy.addObjects(List.of(landmark));
        hierarchy.getSelectionModel().setSelectedObject(landmark);
        hierarchy.fireHierarchyChangedEvent(this);

        Dialogs.showInfoNotification(TITLE, String.format("Added LM-%02d at the view center. Drag it into place.", id));
    }
}
