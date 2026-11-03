package qupath.ext.anchor.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ChoiceDialog;
import javafx.stage.DirectoryChooser;
import qupath.ext.anchor.io.LandmarkIO;
import qupath.ext.anchor.model.ImageNames;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

/**
 * Export the landmarks of every open image at once: pick a format and a destination folder once, then
 * write one file per image (named after the image). If some files already exist, the user is asked
 * whether to overwrite them or skip them.
 */
public class ExportAllLandmarksCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ExportAllLandmarksCommand.class);
    private static final String TITLE = "Export all landmarks";

    private record Target(ImageData<BufferedImage> imageData, File file) {}

    private final QuPathGUI qupath;

    public ExportAllLandmarksCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        List<QuPathViewer> viewers = new ArrayList<>();
        for (QuPathViewer v : qupath.getAllViewers()) {
            if (v.getImageData() != null && !LandmarkIO.landmarks(v.getImageData()).isEmpty())
                viewers.add(v);
        }
        if (viewers.isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No open image has landmarks to export.");
            return;
        }

        ChoiceDialog<String> formatDialog = new ChoiceDialog<>("GeoJSON", List.of("GeoJSON", "CSV"));
        formatDialog.setTitle(TITLE);
        formatDialog.setHeaderText("Export landmarks from " + viewers.size() + " image(s)");
        formatDialog.setContentText("Format:");
        if (qupath.getStage() != null)
            formatDialog.initOwner(qupath.getStage());
        Optional<String> format = formatDialog.showAndWait();
        if (format.isEmpty())
            return;
        boolean csv = "CSV".equals(format.get());
        String ext = csv ? ".csv" : ".geojson";

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose a folder to save the landmark files");
        File dir = dirChooser.showDialog(qupath.getStage());
        if (dir == null)
            return;

        // Resolve one output file per image (disambiguating duplicate names).
        List<Target> targets = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (QuPathViewer v : viewers) {
            var imageData = v.getImageData();
            String base = LandmarkIO.baseFileName(imageData);
            String fileName = base;
            int k = 1;
            while (!usedNames.add((fileName + ext).toLowerCase()))
                fileName = base + "_" + (++k);
            targets.add(new Target(imageData, new File(dir, fileName + ext)));
        }

        long existing = targets.stream().filter(t -> t.file().exists()).count();
        boolean overwrite = true;
        if (existing > 0) {
            // Yes/No so "No = skip" is a real button (a plain confirm only offers OK/Cancel).
            overwrite = Dialogs.showYesNoDialog(TITLE,
                    existing + " file(s) already exist in that folder.\n"
                            + "Yes = overwrite them, No = skip them.");
        }

        int exported = 0;
        int totalLandmarks = 0;
        int skipped = 0;
        List<String> perImage = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Target t : targets) {
            if (t.file().exists() && !overwrite) {
                skipped++;
                continue;
            }
            try {
                int count = LandmarkIO.landmarks(t.imageData()).size();
                if (csv)
                    LandmarkIO.exportCsv(t.imageData(), t.file());
                else
                    LandmarkIO.exportGeoJson(t.imageData(), t.file());
                exported++;
                totalLandmarks += count;
                perImage.add(String.format("  %s: %d landmark(s)",
                        ImageNames.fileName(t.imageData().getServer()), count));
            } catch (IOException e) {
                logger.error("Failed to export {}", t.file().getName(), e);
                failed.add(t.file().getName());
            }
        }

        StringBuilder summary = new StringBuilder(String.format(
                "Exported %d landmark(s) from %d image(s) to %s:%n", totalLandmarks, exported, dir.getName()));
        for (String line : perImage)
            summary.append(line).append(System.lineSeparator());
        if (skipped > 0)
            summary.append("Skipped ").append(skipped).append(" existing file(s).").append(System.lineSeparator());
        if (!failed.isEmpty())
            summary.append("Failed: ").append(String.join(", ", failed)).append(System.lineSeparator());
        logger.info("{}", summary.toString().trim());
        Dialogs.showInfoNotification(TITLE, summary.toString().trim());
    }
}
