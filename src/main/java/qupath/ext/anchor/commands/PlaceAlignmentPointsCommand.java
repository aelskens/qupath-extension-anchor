package qupath.ext.anchor.commands;

import java.util.ArrayList;
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
 * Place three alignment control points (role {@link LandmarkRole#MANUAL}) in a spread, non-collinear
 * triangle on the current image. Numbering continues from the highest existing landmark id. Place
 * them first on each image (while empty) so they share ids 1..3 across images; the user then drags
 * each onto matching features and runs {@link AlignAndSyncCommand}.
 */
public class PlaceAlignmentPointsCommand implements Runnable {

    private static final String TITLE = "Place alignment points";

    /** Fractional positions of the three seeded points (non-collinear). */
    private static final double[][] FRACTIONS = {{0.30, 0.35}, {0.70, 0.35}, {0.50, 0.70}};

    private final QuPathGUI qupath;

    public PlaceAlignmentPointsCommand(QuPathGUI qupath) {
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
        boolean alreadyPresent = hierarchy.getAnnotationObjects().stream()
                .anyMatch(o -> Landmarks.getRole(o) == LandmarkRole.MANUAL);
        if (alreadyPresent) {
            boolean proceed = Dialogs.showConfirmDialog(TITLE,
                    "This image already has manually-placed landmarks. Add another three "
                            + "(numbered after the existing landmarks)?");
            if (!proceed)
                return;
        }

        SessionInfo session = AnchorSession.getInstance().ensureSessionInfo(TITLE);
        if (session == null)
            return;

        var server = imageData.getServer();
        double w = server.getWidth();
        double h = server.getHeight();
        int startId = Landmarks.nextId(hierarchy.getAnnotationObjects());
        ImagePlane plane = ImagePlane.getDefaultPlane();

        List<PathObject> points = new ArrayList<>(FRACTIONS.length);
        for (int i = 0; i < FRACTIONS.length; i++) {
            double x = FRACTIONS[i][0] * w;
            double y = FRACTIONS[i][1] * h;
            points.add(Landmarks.create(startId + i, x, y, LandmarkRole.MANUAL, plane, session));
        }

        hierarchy.addObjects(points);
        hierarchy.fireHierarchyChangedEvent(this);
        int endId = startId + points.size() - 1;
        Dialogs.showInfoNotification(TITLE, String.format(
                "Placed 3 alignment points (LM-%02d..LM-%02d). Drag each onto the matching feature, "
                        + "repeat on the other image(s), then run 'Align & sync viewers'.", startId, endId));
    }
}
