package qupath.ext.anchor.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import qupath.ext.anchor.overlay.ColorProjectionManager;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.ext.anchor.viewer.ReferenceIndicator;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.ViewerManager;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Open all project images that share a chosen metadata value (a "group") into a single row of the
 * multi-viewer grid, ready to be aligned and annotated together. Images are cleared first so the grid
 * can resize to one row of exactly the group's size, then the group is loaded one image per cell and
 * each cell is fitted.
 * <p>
 * Loading a new series invalidates any prior alignment, so this starts fresh: it stops aligned sync
 * (and QuPath's built-in sync), clears the reference/overlay state, and resets each new viewer
 * (rotation cleared, zoom-to-fit).
 */
public class OpenImageGroupCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OpenImageGroupCommand.class);
    private static final String TITLE = "Open image group";

    private final QuPathGUI qupath;
    private final String filter;

    /**
     * @param filter project-search-style metadata filter: {@code key=value} pairs joined by
     *               {@code |} (e.g. {@code group=2|stain=HE}); an image is in the group when it matches
     *               every pair. A leading {@code |} is ignored.
     */
    public OpenImageGroupCommand(QuPathGUI qupath, String filter) {
        this.qupath = qupath;
        this.filter = filter;
    }

    /** Distinct metadata keys across all project entries (sorted); empty if there is no project. */
    public static List<String> metadataKeys(QuPathGUI qupath) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null)
            return List.of();
        TreeSet<String> keys = new TreeSet<>();
        for (ProjectImageEntry<BufferedImage> e : project.getImageList())
            keys.addAll(e.getMetadata().keySet());
        return new ArrayList<>(keys);
    }

    /** Parse a {@code key=value|key2=value2} filter into ordered pairs; empty/blank parts are ignored. */
    public static Map<String, String> parseFilter(String filter) {
        Map<String, String> result = new LinkedHashMap<>();
        if (filter == null)
            return result;
        for (String token : filter.split("\\|")) {
            int eq = token.indexOf('=');
            if (eq <= 0)
                continue;
            String key = token.substring(0, eq).trim();
            if (!key.isEmpty())
                result.put(key, token.substring(eq + 1).trim());
        }
        return result;
    }

    /** Project images matching every {@code key=value} pair in {@code filters} (all must match). */
    private static List<ProjectImageEntry<BufferedImage>> matching(Project<BufferedImage> project,
                                                                   Map<String, String> filters) {
        List<ProjectImageEntry<BufferedImage>> result = new ArrayList<>();
        for (ProjectImageEntry<BufferedImage> e : project.getImageList()) {
            boolean ok = true;
            for (Map.Entry<String, String> f : filters.entrySet()) {
                if (!f.getValue().equals(e.getMetadata().get(f.getKey()))) {
                    ok = false;
                    break;
                }
            }
            if (ok)
                result.add(e);
        }
        return result;
    }

    /** Number of project images matching the filter (0 if no project or empty filter). */
    public static int groupSize(QuPathGUI qupath, String filter) {
        Project<BufferedImage> project = qupath.getProject();
        Map<String, String> filters = parseFilter(filter);
        if (project == null || filters.isEmpty())
            return 0;
        return matching(project, filters).size();
    }

    private static String describe(Map<String, String> filters) {
        List<String> parts = new ArrayList<>();
        filters.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(", ", parts);
    }

    /** A viewer's column in a single-row grid, read from the SplitPane data model; MAX_VALUE if none. */
    private static int columnOf(QuPathViewer viewer) {
        Node view = viewer.getView();
        if (view == null)
            return Integer.MAX_VALUE;
        SplitPane rowPane = nearestSplitPane(view);
        if (rowPane == null)
            return Integer.MAX_VALUE;
        int col = rowPane.getItems().indexOf(view);
        return col < 0 ? Integer.MAX_VALUE : col;
    }

    /** Nearest SplitPane ancestor of {@code node}, or null (the SplitPane skin wraps items). */
    private static SplitPane nearestSplitPane(Node node) {
        for (Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof SplitPane sp)
                return sp;
        }
        return null;
    }

    @Override
    public void run() {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage(TITLE, "Open a QuPath project first; its images carry the grouping metadata.");
            return;
        }
        Map<String, String> filters = parseFilter(filter);
        if (filters.isEmpty()) {
            Dialogs.showErrorMessage(TITLE,
                    "Enter a filter, e.g. group=2 (combine columns with |, e.g. group=2|stain=HE).");
            return;
        }

        List<ProjectImageEntry<BufferedImage>> group = matching(project, filters);
        if (group.isEmpty()) {
            Dialogs.showErrorMessage(TITLE, "No project image matches " + describe(filters) + ".");
            return;
        }
        group.sort(Comparator.comparing(ProjectImageEntry::getImageName));

        // A new group invalidates any prior alignment: stop sync and clear reference/overlay state
        // before touching the viewers, so those listeners don't react to each image swap.
        AlignSyncManager.getInstance().stop();
        AlignSyncManager.getInstance().setReferenceViewer(null);
        ReferenceIndicator.clear(qupath);
        ColorProjectionManager.getInstance().onAlignmentChanged(qupath);

        int k = group.size();
        ViewerManager vm = qupath.getViewerManager();
        vm.setSynchronizeViewers(false);   // also drop QuPath's built-in sync, so the new series is fresh
        // Clear every image first: setGridSize only counts viewers that still hold an image, so
        // clearing lets the grid actually resize (close viewers), not just blank surplus panes.
        for (QuPathViewer v : new ArrayList<>(vm.getAllViewers())) {
            if (v.hasServer())
                v.resetImageData();
        }
        // A single row of k cells. setGridSize is synchronous and the viewers are in the scene at once,
        // so we can resolve their left-to-right order now (getAllViewers() is insertion order, which is
        // not guaranteed, so sort by the SplitPane column index).
        vm.setGridSize(1, k);
        List<QuPathViewer> ordered = new ArrayList<>(vm.getAllViewers());
        ordered.sort(Comparator.comparingInt(OpenImageGroupCommand::columnOf));

        // Defer the load one pulse so the new cells have a real size, then fit one pulse later still.
        // This is the timing that fitted correctly before: setImageData auto-fits using the viewer
        // size, so both the load and the final fit must run after layout has sized the new cells.
        Platform.runLater(() -> loadGroup(ordered, group, k));
    }

    /** Load one image per cell (left to right), then fit each on a later pulse; report the result. */
    private void loadGroup(List<QuPathViewer> ordered, List<ProjectImageEntry<BufferedImage>> group, int k) {
        List<String> failed = new ArrayList<>();
        List<QuPathViewer> loadedViewers = new ArrayList<>();
        for (int i = 0; i < ordered.size() && i < k; i++) {
            try {
                QuPathViewer viewer = ordered.get(i);
                viewer.setImageData(group.get(i).readImageData());
                loadedViewers.add(viewer);
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not open '{}' into a viewer", group.get(i).getImageName(), e);
                failed.add(group.get(i).getImageName());
            }
        }
        int loaded = loadedViewers.size();
        // On a later pulse (once the cells are laid out), start each fresh: clear any rotation left
        // over from a previous aligned sync, then fit. Sync itself was already stopped in run().
        Platform.runLater(() -> loadedViewers.forEach(viewer -> {
            viewer.setRotation(0);
            viewer.zoomToFit();
        }));

        StringBuilder msg = new StringBuilder(String.format(
                "Opened %d/%d image(s) for [%s] in a single row.", loaded, k, filter));
        if (k > ordered.size())
            msg.append(String.format(" %d not shown (grid fits %d).", k - ordered.size(), ordered.size()));
        if (!failed.isEmpty())
            msg.append(" Failed: ").append(failed);
        logger.info(msg.toString());
        Dialogs.showInfoNotification(TITLE, msg.toString());
    }
}
