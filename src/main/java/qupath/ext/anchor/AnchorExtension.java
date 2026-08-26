package qupath.ext.anchor;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import qupath.ext.anchor.commands.AddLandmarkCommand;
import qupath.ext.anchor.commands.AlignAndSyncCommand;
import qupath.ext.anchor.commands.ClearLandmarksCommand;
import qupath.ext.anchor.commands.ExportAllLandmarksCommand;
import qupath.ext.anchor.commands.ExportLandmarksCommand;
import qupath.ext.anchor.commands.LoadLandmarksCommand;
import qupath.ext.anchor.commands.PlaceAlignmentPointsCommand;
import qupath.ext.anchor.commands.SeedGridCommand;
import qupath.ext.anchor.commands.ViewerCommands;
import qupath.ext.anchor.overlay.ColorProjectionManager;
import qupath.ext.anchor.ui.ShowAnchorPanelCommand;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Anchor: a QuPath extension for annotating corresponding landmark points across two or more
 * whole-slide images, fitting spatial transforms between them, and aligning / synchronizing their
 * viewers from a small set of manually-placed control points.
 * <p>
 * The control panel ({@code Extensions > Anchor > Show Anchor panel}) is the primary UI; the menu
 * items carry keyboard accelerators so every action has a shortcut.
 */
public class AnchorExtension implements QuPathExtension {

    private static final String EXTENSION_NAME = "Anchor";
    private static final String EXTENSION_DESCRIPTION =
            "Annotate corresponding landmarks across two or more images, align and synchronize their "
                    + "viewers, and blend them into a false-color overlay. Open the Anchor panel from this "
                    + "menu (Shortcut+Alt+A).";
    // Built and verified against QuPath 0.7.0 (stable, released 2026-03-02).
    private static final Version QUPATH_VERSION = Version.parse("v0.7.0");

    private boolean installed = false;
    // Retained so the panel's window persists across menu clicks.
    private ShowAnchorPanelCommand showPanel;

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (installed)
            return;
        installed = true;
        showPanel = new ShowAnchorPanelCommand(qupath);

        Menu menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
        menu.getItems().addAll(
                item("Show Anchor panel", "Shortcut+Alt+A", () -> showPanel.run()),
                new SeparatorMenuItem(),

                header("Alignment & sync"),
                item("Set reference to current viewer", "Shortcut+Alt+F",
                        () -> ViewerCommands.setReferenceToCurrent(qupath)),
                item("Place alignment points", "Shortcut+Alt+P",
                        () -> new PlaceAlignmentPointsCommand(qupath).run()),
                item("Align & sync viewers", "Shortcut+Alt+Y",
                        () -> new AlignAndSyncCommand(qupath).run()),
                item("Stop aligned sync", "Shortcut+Alt+U",
                        () -> ViewerCommands.stopAlignedSync(qupath)),
                item("Reset views", "Shortcut+Alt+R",
                        () -> ViewerCommands.resetViews(qupath)),
                new SeparatorMenuItem(),

                header("Landmarks"),
                item("Add landmark", "Shortcut+Alt+L",
                        () -> new AddLandmarkCommand(qupath).run()),
                item("Seed landmark grid...", "Shortcut+Alt+G",
                        () -> new SeedGridCommand(qupath).run()),
                item("Clear landmarks", null,
                        () -> new ClearLandmarksCommand(qupath).run()),
                item("Load landmarks...", null,
                        () -> new LoadLandmarksCommand(qupath).run()),
                item("Export all landmarks...", "Shortcut+Alt+E",
                        () -> new ExportAllLandmarksCommand(qupath).run()),
                item("Export landmarks (selected view)...", null,
                        () -> new ExportLandmarksCommand(qupath).run()),
                new SeparatorMenuItem(),

                header("Overlay"),
                item("Toggle overlay in viewer", "Shortcut+Alt+O",
                        () -> ColorProjectionManager.getInstance().toggleInViewer(qupath)));
    }

    private static MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem menuItem = new MenuItem(text);
        menuItem.setOnAction(e -> action.run());
        if (accelerator != null)
            menuItem.setAccelerator(KeyCombination.valueOf(accelerator));
        return menuItem;
    }

    /** A non-interactive, bold section title within the menu. */
    private static MenuItem header(String text) {
        MenuItem h = new MenuItem(text);
        h.setDisable(true);
        h.setStyle("-fx-font-weight: bold;");
        return h;
    }

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    @Override
    public Version getQuPathVersion() {
        return QUPATH_VERSION;
    }
}
