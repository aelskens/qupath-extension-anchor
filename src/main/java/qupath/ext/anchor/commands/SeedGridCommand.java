package qupath.ext.anchor.commands;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.anchor.model.Landmarks;
import qupath.ext.anchor.model.LandmarkRole;
import qupath.ext.anchor.model.SessionInfo;
import qupath.ext.anchor.seeding.GridSeeder;
import qupath.ext.anchor.session.AnchorSession;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;

/**
 * Seed a most-square grid of numbered, colored landmark point annotations over the current image.
 * Points are created with role {@link LandmarkRole#GRID}; numbering continues from the highest
 * existing landmark id (so a grid seeded after alignment points starts after them).
 */
public class SeedGridCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SeedGridCommand.class);
    private static final String TITLE = "Seed landmark grid";

    private final QuPathGUI qupath;
    private final Integer requestedN;

    /** Prompts for N. */
    public SeedGridCommand(QuPathGUI qupath) {
        this(qupath, null);
    }

    /** Uses the given N (from the panel); pass {@code null} to prompt. */
    public SeedGridCommand(QuPathGUI qupath, Integer n) {
        this.qupath = qupath;
        this.requestedN = n;
    }

    @Override
    public void run() {
        var viewer = qupath.getViewer();
        var imageData = viewer == null ? null : viewer.getImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage(TITLE, "Open an image first.");
            return;
        }

        int n;
        if (requestedN != null) {
            n = requestedN;
        } else {
            String nText = Dialogs.showInputDialog(TITLE, "Number of landmarks (N):", "9");
            if (nText == null)
                return;
            try {
                n = Integer.parseInt(nText.trim());
            } catch (NumberFormatException e) {
                Dialogs.showErrorMessage(TITLE, "N must be a whole number, got: " + nText);
                return;
            }
        }
        if (n < 1) {
            Dialogs.showErrorMessage(TITLE, "N must be >= 1, got " + n);
            return;
        }

        SessionInfo session = AnchorSession.getInstance().ensureSessionInfo(TITLE);
        if (session == null)
            return;

        var server = imageData.getServer();
        int width = server.getWidth();
        int height = server.getHeight();

        List<Point2D> positions;
        try {
            positions = GridSeeder.seed(n, width, height, GridSeeder.DEFAULT_MARGIN_FRACTION);
        } catch (IllegalArgumentException e) {
            Dialogs.showErrorMessage(TITLE, e.getMessage());
            return;
        }

        var hierarchy = imageData.getHierarchy();
        int startId = Landmarks.nextId(hierarchy.getAnnotationObjects());
        ImagePlane plane = ImagePlane.getDefaultPlane();

        List<PathObject> landmarks = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            Point2D p = positions.get(i);
            landmarks.add(Landmarks.create(startId + i, p.getX(), p.getY(), LandmarkRole.GRID, plane, session));
        }

        hierarchy.addObjects(landmarks);
        hierarchy.fireHierarchyChangedEvent(this);

        int endId = startId + landmarks.size() - 1;
        logger.info("Seeded {} landmark(s) (LM-{}..LM-{}) on {}x{} image",
                landmarks.size(), startId, endId, width, height);
        Dialogs.showInfoNotification(TITLE, String.format(
                "Seeded %d landmark(s): LM-%02d..LM-%02d.", landmarks.size(), startId, endId));
    }
}
