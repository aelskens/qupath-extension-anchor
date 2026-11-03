package qupath.ext.anchor.commands;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import qupath.ext.anchor.model.ImageNames;
import qupath.ext.anchor.model.LandmarkKeys;
import qupath.ext.anchor.model.Landmarks;
import qupath.ext.anchor.model.LandmarkRole;
import qupath.ext.anchor.model.SessionInfo;
import qupath.ext.anchor.session.AnchorSession;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.ext.anchor.viewer.AlignedSyncController;
import qupath.ext.anchor.viewer.CursorTracker;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;

/**
 * Add a single landmark (role {@link LandmarkRole#MANUAL}) with the lowest free id of the current
 * image (filling gaps left by deleted landmarks). The user then drags it onto the feature of interest.
 * <p>
 * Placement: when {@code atCursor} is set (keyboard shortcut) and the mouse is over the active viewer,
 * the point goes under the cursor; otherwise (panel button, cursor elsewhere) at the view center.
 * <p>
 * While aligned sync is active, the same landmark id is also added to every other synced image at the
 * corresponding location (mapped through the fitted alignment), but only to images that do not already
 * have a landmark with that id.
 */
public class AddLandmarkCommand implements Runnable {

    private static final String TITLE = "Add landmark";

    private final QuPathGUI qupath;
    private final boolean atCursor;

    /** Add at the view center (e.g. from a button, where the cursor is not over the viewer). */
    public AddLandmarkCommand(QuPathGUI qupath) {
        this(qupath, false);
    }

    /**
     * @param atCursor place the landmark under the mouse cursor when it is over the active viewer
     *                 (falls back to the view center otherwise)
     */
    public AddLandmarkCommand(QuPathGUI qupath, boolean atCursor) {
        this.qupath = qupath;
        this.atCursor = atCursor;
    }

    @Override
    public void run() {
        var viewer = qupath.getViewer();
        var imageData = viewer == null ? null : viewer.getImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage(TITLE, "Open an image first.");
            return;
        }

        // Resolve the location before any dialog (the annotator prompt) can move focus/cursor.
        Point2D cursor = atCursor ? CursorTracker.getInstance().imagePointUnderCursor(viewer) : null;
        Point2D location = cursor != null
                ? cursor
                : new Point2D.Double(viewer.getCenterPixelX(), viewer.getCenterPixelY());

        SessionInfo session = AnchorSession.getInstance().ensureSessionInfo(TITLE);
        if (session == null)
            return;

        var hierarchy = imageData.getHierarchy();
        int id = Landmarks.firstFreeId(hierarchy.getAnnotationObjects());

        PathObject landmark = Landmarks.create(id, location.getX(), location.getY(), LandmarkRole.MANUAL,
                ImagePlane.getDefaultPlane(), session);
        hierarchy.addObjects(List.of(landmark));
        hierarchy.getSelectionModel().setSelectedObject(landmark);
        hierarchy.fireHierarchyChangedEvent(this);

        String where = cursor != null ? "at the cursor" : "at the view center";
        String message = String.format("Added %s %s. Drag it into place.", LandmarkKeys.nameForId(id), where);

        String synced = addToSyncedViewers(viewer, id, location, session);
        Dialogs.showInfoNotification(TITLE, synced == null ? message : message + "\n" + synced);
    }

    /**
     * If aligned sync is active and includes {@code source}, add landmark {@code id} to every other
     * synced image that lacks it, at the location corresponding to {@code location}.
     *
     * @return a short summary for the notification, or {@code null} if sync is not active
     */
    private String addToSyncedViewers(QuPathViewer source, int id, Point2D location, SessionInfo session) {
        AlignedSyncController controller = AlignSyncManager.getInstance().getActiveController();
        if (controller == null || !controller.contains(source))
            return null;

        int added = 0;
        int alreadyPresent = 0;
        List<String> failed = new ArrayList<>();
        for (QuPathViewer target : controller.getViewers()) {
            if (target == source)
                continue;
            var targetData = target.getImageData();
            if (targetData == null)
                continue;
            var targetHierarchy = targetData.getHierarchy();
            if (Landmarks.usedIds(targetHierarchy.getAnnotationObjects()).contains(id)) {
                alreadyPresent++;
                continue;
            }
            Point2D mapped = controller.mapPoint(source, target, location);
            var server = targetData.getServer();
            if (mapped == null || !Double.isFinite(mapped.getX()) || !Double.isFinite(mapped.getY())
                    || mapped.getX() < 0 || mapped.getY() < 0
                    || mapped.getX() > server.getWidth() || mapped.getY() > server.getHeight()) {
                failed.add(ImageNames.fileName(server));
                continue;
            }
            PathObject copy = Landmarks.create(id, mapped.getX(), mapped.getY(), LandmarkRole.MANUAL,
                    ImagePlane.getDefaultPlane(), session);
            targetHierarchy.addObjects(List.of(copy));
            targetHierarchy.fireHierarchyChangedEvent(this);
            added++;
        }

        StringBuilder sb = new StringBuilder(String.format("Synced: also added to %d other image(s)", added));
        if (alreadyPresent > 0)
            sb.append(String.format("; %d already had %s", alreadyPresent, LandmarkKeys.nameForId(id)));
        if (!failed.isEmpty())
            sb.append("; could not map into ").append(String.join(", ", failed));
        return sb.append('.').toString();
    }
}
