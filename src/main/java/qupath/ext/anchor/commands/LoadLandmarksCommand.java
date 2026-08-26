package qupath.ext.anchor.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.FileChooser;
import qupath.ext.anchor.io.LandmarkIO;
import qupath.ext.anchor.model.Landmarks;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathObject;

/**
 * Load a landmark point set (GeoJSON) into the current image. Imported objects keep their name and
 * metadata (landmarkId, role, annotator, ...), so previously-exported landmarks come back as Anchor
 * landmarks. Coordinates are read in full-resolution image pixels, so the file must come from the same
 * (or a geometrically identical) image.
 */
public class LoadLandmarksCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(LoadLandmarksCommand.class);
    private static final String TITLE = "Load landmarks";

    private final QuPathGUI qupath;

    public LoadLandmarksCommand(QuPathGUI qupath) {
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

        FileChooser chooser = new FileChooser();
        chooser.setTitle(TITLE);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("GeoJSON (*.geojson, *.json)", "*.geojson", "*.json"));
        File file = chooser.showOpenDialog(qupath.getStage());
        if (file == null)
            return;

        List<PathObject> objects;
        try {
            objects = LandmarkIO.load(imageData, file);
        } catch (IOException e) {
            logger.error("Could not read landmarks", e);
            Dialogs.showErrorMessage(TITLE, "Could not read the file: " + e.getMessage());
            return;
        }
        if (objects.isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No objects found in the file.");
            return;
        }

        long landmarkCount = objects.stream().filter(Landmarks::isLandmark).count();
        logger.info("Loaded {} object(s) ({} landmarks) from {}", objects.size(), landmarkCount, file.getName());
        Dialogs.showInfoNotification(TITLE, String.format("Loaded %d object(s)%s into the current image.",
                objects.size(), landmarkCount > 0 ? " (" + landmarkCount + " landmarks)" : ""));
    }
}
