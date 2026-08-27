package qupath.ext.anchor.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.FileChooser;
import qupath.ext.anchor.io.LandmarkIO;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathObject;

/**
 * Export the current image's landmark (LM-*) annotations. GeoJSON by default (full-resolution pixel
 * coordinates, metadata under {@code properties}); CSV when the CSV filter / a .csv name is chosen.
 * The suggested file name matches the image file name; if the chosen file exists, the user is asked to
 * overwrite or pick another name.
 */
public class ExportLandmarksCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ExportLandmarksCommand.class);
    private static final String TITLE = "Export landmarks";

    private final QuPathGUI qupath;

    public ExportLandmarksCommand(QuPathGUI qupath) {
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

        List<PathObject> landmarks = LandmarkIO.landmarks(imageData);
        if (landmarks.isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No landmark (LM-*) annotations to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(TITLE);
        chooser.setInitialFileName(LandmarkIO.baseFileName(imageData) + ".geojson");
        FileChooser.ExtensionFilter geojson = new FileChooser.ExtensionFilter("GeoJSON (*.geojson)", "*.geojson");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv");
        chooser.getExtensionFilters().addAll(geojson, csv);
        chooser.setSelectedExtensionFilter(geojson);

        File file;
        while (true) {
            File chosen = chooser.showSaveDialog(qupath.getStage());
            if (chosen == null)
                return;
            FileChooser.ExtensionFilter selected = chooser.getSelectedExtensionFilter();
            boolean asCsv = chosen.getName().toLowerCase().endsWith(".csv")
                    || (selected != null && selected.getExtensions().contains("*.csv"));
            file = ensureExtension(chosen, asCsv ? ".csv" : ".geojson");
            if (file.exists()) {
                // Yes/No so "No = pick another name" is a real button (a plain confirm only has OK/Cancel).
                boolean overwrite = Dialogs.showYesNoDialog(TITLE,
                        "\"" + file.getName() + "\" already exists.\nYes = overwrite, No = pick another name.");
                if (!overwrite) {
                    chooser.setInitialFileName(file.getName());
                    continue;
                }
            }
            break;
        }

        try {
            LandmarkIO.export(imageData, file);
        } catch (IOException e) {
            logger.error("Export failed", e);
            Dialogs.showErrorMessage(TITLE, "Export failed: " + e.getMessage());
            return;
        }

        logger.info("Exported {} landmark(s) to {}", landmarks.size(), file.getAbsolutePath());
        Dialogs.showInfoNotification(TITLE,
                String.format("Exported %d landmark(s) to %s.", landmarks.size(), file.getName()));
    }

    private static File ensureExtension(File file, String ext) {
        return file.getName().toLowerCase().endsWith(ext) ? file : new File(file.getParentFile(), file.getName() + ext);
    }
}
