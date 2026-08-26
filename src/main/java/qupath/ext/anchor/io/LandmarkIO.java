package qupath.ext.anchor.io;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

import qupath.ext.anchor.model.ImageNames;
import qupath.ext.anchor.model.LandmarkKeys;
import qupath.ext.anchor.model.Landmarks;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
import qupath.lib.io.PathIO.GeoJsonExportOptions;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Headless, GUI-free load/export of landmark point sets, suitable for Groovy batch scripts
 * (e.g. "Run for project"). No viewers, file choosers, or dialogs are involved. Coordinates are
 * full-resolution image pixels; loading assumes the file matches the target image's geometry.
 */
public final class LandmarkIO {

    private LandmarkIO() {}

    /** The image's landmark (LM-*) annotations, sorted by id. */
    public static List<PathObject> landmarks(ImageData<BufferedImage> imageData) {
        return imageData.getHierarchy().getAnnotationObjects().stream()
                .filter(Landmarks::isLandmark)
                .sorted(Comparator.comparingInt(o -> Landmarks.getId(o).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    /** A filesystem-safe base file name derived from the image's source file name (no extension). */
    public static String baseFileName(ImageData<BufferedImage> imageData) {
        return ImageNames.baseName(imageData.getServer());
    }

    /** Export the image's landmarks; format chosen by the file extension (.csv -> CSV, else GeoJSON). */
    public static void export(ImageData<BufferedImage> imageData, File file) throws IOException {
        if (file.getName().toLowerCase().endsWith(".csv"))
            writeCsv(file, landmarks(imageData));
        else
            writeGeoJson(file, landmarks(imageData));
    }

    public static void exportGeoJson(ImageData<BufferedImage> imageData, File file) throws IOException {
        writeGeoJson(file, landmarks(imageData));
    }

    public static void exportCsv(ImageData<BufferedImage> imageData, File file) throws IOException {
        writeCsv(file, landmarks(imageData));
    }

    /**
     * Load objects from a GeoJSON file and add them to the image's hierarchy.
     *
     * @return the objects added (empty if the file had none)
     */
    public static List<PathObject> load(ImageData<BufferedImage> imageData, File file) throws IOException {
        List<PathObject> objects = PathIO.readObjects(file);
        if (!objects.isEmpty()) {
            PathObjectHierarchy hierarchy = imageData.getHierarchy();
            hierarchy.addObjects(objects);
            hierarchy.fireHierarchyChangedEvent(LandmarkIO.class);
        }
        return objects;
    }

    public static void writeGeoJson(File file, List<PathObject> landmarks) throws IOException {
        PathIO.exportObjectsAsGeoJSON(file, landmarks,
                GeoJsonExportOptions.FEATURE_COLLECTION, GeoJsonExportOptions.PRETTY_JSON);
    }

    public static void writeCsv(File file, List<PathObject> landmarks) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            w.println("name,landmarkId,role,x,y,annotator,mode,sessionId,createdOrModified");
            for (PathObject o : landmarks) {
                Point2D p = Landmarks.getPoint(o);
                var m = o.getMetadata();
                w.println(String.join(",",
                        csv(o.getName()),
                        csv(m.get(LandmarkKeys.LANDMARK_ID)),
                        csv(m.get(LandmarkKeys.ROLE)),
                        p == null ? "" : Double.toString(p.getX()),
                        p == null ? "" : Double.toString(p.getY()),
                        csv(m.get(LandmarkKeys.ANNOTATOR)),
                        csv(m.get(LandmarkKeys.MODE)),
                        csv(m.get(LandmarkKeys.SESSION_ID)),
                        csv(m.get(LandmarkKeys.CREATED_OR_MODIFIED))));
            }
        }
    }

    private static String csv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}
