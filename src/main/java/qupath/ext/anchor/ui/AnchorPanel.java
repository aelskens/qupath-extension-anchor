package qupath.ext.anchor.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import qupath.ext.anchor.commands.AddLandmarkCommand;
import qupath.ext.anchor.commands.AlignAndSyncCommand;
import qupath.ext.anchor.commands.ClearLandmarksCommand;
import qupath.ext.anchor.commands.ExportAllLandmarksCommand;
import qupath.ext.anchor.commands.ExportLandmarksCommand;
import qupath.ext.anchor.commands.LoadLandmarksCommand;
import qupath.ext.anchor.commands.OpenImageGroupCommand;
import qupath.ext.anchor.commands.PlaceAlignmentPointsCommand;
import qupath.ext.anchor.commands.SeedGridCommand;
import qupath.ext.anchor.commands.ViewerCommands;
import qupath.ext.anchor.model.PointSource;
import qupath.ext.anchor.model.SessionInfo;
import qupath.ext.anchor.overlay.ColorProjectionManager;
import qupath.ext.anchor.session.AnchorSession;
import qupath.ext.anchor.transform.TransformType;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;

/**
 * The Anchor control panel: the primary UI, gathering the workflow (alignment, landmarks, sync) into
 * one place with live status, per-control help tooltips, and a Help overview.
 */
public class AnchorPanel extends SplitPane {

    private static final String HELP_TEXT = """
            Anchor keeps two or more images navigating together from shared landmarks.

            Typical workflow
            1. Open the images in a grid (View > Multi-view), or use 'Dataset' to open a project
               group: type a metadata filter like group=2 (combine columns with |, e.g.
               group=2|stain=HE), then 'Open group in grid' loads the matching images into a single
               row of viewers automatically.
            2. (Optional) 'Set reference to current viewer' - the image the others align to (badged).
            3. 'Place alignment points' on each image, then drag each point onto the SAME feature.
            4. Choose 'Fit from' (Manual points).
            5. 'Align & sync' - links the viewers with a similarity fit.
            6. Pan/zoom any viewer; the others follow. 'Stop sync' to unlink.
            7. 'Reset views' clears rotation and fits; after Stop sync it restores the original view.

            Landmarks
            - 'Seed grid': N points to drag onto features (for annotation / agreement studies).
            - 'Add landmark': one point at the view center.
            - Numbering continues from the highest existing id (so points added after the first
              three are numbered 4, 5, ...).
            - Landmark labels (LM-xx) use QuPath's native point-annotation names (toggle them with
              the toolbar 'Show names' button); they follow each viewer's transform.

            Sync vs overlay transform
            - The viewer sync is always a similarity (rotation + uniform scale + translation), which is
              all a raw viewer can honestly reproduce.
            - The 'Overlay transform' (Overlay section) applies to the composite only:
              rigid / similarity (>= 2 points), affine (+ shear, >= 3), or TPS (nonlinear, >= 3, 5+
              recommended). Changing it re-fits the overlay while sync is active.

            'Fit from' (which landmarks feed the fit)
            - Manual: the points you placed or added (default).
            - All landmarks: every point, including the grid.
            - Dragged grid points: grid points you have moved from their seeded position.

            Overlay (false-color composite of the aligned images)
            - Each image is tinted a distinct color and blended; overlaps brighten toward white.
              H&E / brightfield is auto-inverted; each image has its own opacity (0 hides it).
            - 'Show overlay' draws it in the panel preview (drag the divider to size it).
            - 'Show in selected viewer' also draws it in place, in the selected viewer, so you can
              judge the true (including TPS elastic) alignment on the image itself.

            Export
            - 'Export all landmarks' writes one GeoJSON (or CSV) file per open image to a folder;
              'Export landmarks (selected view)' writes just the current image. Coordinates are
              full-resolution image pixels; ids/annotator/mode/session are kept under GeoJSON
              properties, so files re-import as Anchor landmarks.

            Modes (assisted / blind) affect loading PRIOR annotations for agreement studies, not
            the alignment itself.
            """;

    private final QuPathGUI qupath;
    private final Label sessionLabel = new Label();
    private final Label referenceLabel = new Label();
    private final Label statusLabel = new Label();
    private final ComboBox<TransformType> transformCombo = new ComboBox<>();
    private final ComboBox<PointSource> sourceCombo = new ComboBox<>();
    private final ComboBox<String> modeCombo = new ComboBox<>();
    private final TextField filterField = new TextField();
    private final Label groupInfoLabel = new Label();
    private final Spinner<Integer> nSpinner = new Spinner<>(1, 999, 9);
    private final CheckBox overlayInViewerCheck = new CheckBox("Show in selected viewer");
    private final Button overlayToggle = new Button("Show overlay");
    private final ImageView overlayPreview = new ImageView();
    private final VBox imageSelectionBox = new VBox(4);
    private double lastDividerPosition = 0.55;

    public AnchorPanel(QuPathGUI qupath) {
        this.qupath = qupath;
        setPrefSize(720, 620);

        Label title = new Label("Anchor");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Button help = new Button("Help");
        help.setTooltip(new Tooltip("Show the Anchor workflow overview."));
        help.setOnAction(e -> showHelp());
        HBox titleRow = new HBox(6, title, spacer(), help);

        // Session
        modeCombo.getItems().addAll(SessionInfo.MODE_ASSISTED, SessionInfo.MODE_BLIND);
        modeCombo.getSelectionModel().select(AnchorSession.getInstance().mode());
        modeCombo.setTooltip(new Tooltip(
                "Assisted vs blind loading of prior annotations (for agreement studies). "
                        + "Does not affect alignment."));
        modeCombo.setOnAction(e -> {
            AnchorSession.getInstance().setMode(modeCombo.getValue());
            refreshSession();
        });
        Button setAnnotator = button("Set annotator...",
                "Set the annotator id, asked once and reused for the whole session.", () -> {
                    String cur = AnchorSession.getInstance().annotator();
                    String v = Dialogs.showInputDialog("Anchor", "Annotator id:", cur == null ? "annotator-1" : cur);
                    if (v != null) {
                        AnchorSession.getInstance().setAnnotator(v);
                        refreshSession();
                    }
                });
        sessionLabel.setWrapText(true);

        // Dataset (project groups): a project-search-style metadata filter selects the images to open
        // together. key=value pairs joined by | (all must match), so several columns can be combined.
        filterField.setPromptText("e.g. group=2|stain=HE");
        filterField.setTooltip(new Tooltip(
                "Metadata filter: key=value pairs joined by | (all must match), like the project search "
                        + "bar. Example: group=2|stain=HE opens every image whose 'group' is 2 AND 'stain' "
                        + "is HE. Available columns: " + String.join(", ", OpenImageGroupCommand.metadataKeys(qupath))));
        filterField.textProperty().addListener((obs, o, v) -> updateGroupInfo());
        filterField.setOnAction(e -> run(new OpenImageGroupCommand(qupath, filterField.getText())));
        groupInfoLabel.setStyle("-fx-font-style: italic;");
        groupInfoLabel.setWrapText(true);

        // Alignment
        referenceLabel.setWrapText(true);
        // The viewer sync is always a similarity (all a raw viewer can reproduce). The transform combo
        // selects the OVERLAY transform only, and lives in the Color overlay section.
        transformCombo.getItems().addAll(TransformType.RIGID, TransformType.SIMILARITY,
                TransformType.AFFINE, TransformType.TPS);
        transformCombo.getSelectionModel().select(TransformType.AFFINE);
        transformCombo.setTooltip(new Tooltip(
                "Transform used for the OVERLAY only (the viewer sync is always a similarity): rigid = "
                        + "rotate + move (>= 2 points); similarity = + uniform scale (>= 2); affine = + shear "
                        + "(>= 3); TPS = nonlinear warp (>= 3, 5+ recommended)."));
        transformCombo.setOnAction(e -> {
            // If sync is active, re-fit the overlay for the new type without disturbing the sync.
            if (AlignSyncManager.getInstance().isActive())
                new AlignAndSyncCommand(qupath, transformCombo.getValue(), sourceCombo.getValue(), true).run();
        });
        sourceCombo.getItems().addAll(PointSource.values());
        sourceCombo.getSelectionModel().select(PointSource.MANUAL);
        sourceCombo.setTooltip(new Tooltip(
                "Which landmarks feed the fit: Manual = points you placed/added; All = every "
                        + "landmark; Dragged grid = grid points you moved."));
        statusLabel.setWrapText(true);

        // Color overlay toggle
        overlayToggle.setMaxWidth(Double.MAX_VALUE);
        overlayToggle.setTooltip(new Tooltip(
                "Show/hide the false-color composite of the aligned images in the preview on the right "
                        + "(needs an active Align & sync). Drag the divider to size it."));
        overlayToggle.setOnAction(e -> {
            boolean nowEnabled = !ColorProjectionManager.getInstance().isPanelShown();
            ColorProjectionManager.getInstance().setPanelShown(qupath, nowEnabled);
            if (nowEnabled) {
                setDividerPositions(lastDividerPosition);   // reveal the preview pane
            } else {
                double[] pos = getDividerPositions();
                if (pos.length > 0 && pos[0] < 0.98)
                    lastDividerPosition = pos[0];
                setDividerPositions(1.0);                    // collapse the preview pane
            }
            updateOverlayToggle();
        });
        updateOverlayToggle();

        overlayInViewerCheck.setSelected(ColorProjectionManager.getInstance().isInViewer());
        overlayInViewerCheck.setTooltip(new Tooltip(
                "Draw the composite directly in the selected viewer (in place), independent of the panel "
                        + "preview. Shows the true affine/TPS alignment on the image itself. (Shortcut+Alt+O)"));
        overlayInViewerCheck.setOnAction(e ->
                ColorProjectionManager.getInstance().setInViewer(qupath, overlayInViewerCheck.isSelected()));

        // Composite preview (off-screen render); shown in the right pane, sized by the divider.
        overlayPreview.setPreserveRatio(true);
        overlayPreview.setSmooth(true);
        ColorProjectionManager.getInstance().setPreviewView(overlayPreview);
        ColorProjectionManager.getInstance().setOnParticipantsChanged(this::rebuildImageSelection);
        rebuildImageSelection();

        // Landmarks
        nSpinner.setEditable(true);
        nSpinner.setPrefWidth(90);
        nSpinner.setTooltip(new Tooltip("Number of grid points to seed."));


        // Left pane: all controls (including the overlay toggle + per-image opacity).
        VBox controls = new VBox(6);
        controls.setPadding(new Insets(10));
        controls.getChildren().addAll(
                titleRow,
                new Separator(),

                sectionLabel("Session"),
                sessionLabel,
                labeledRow("Mode:", modeCombo),
                fullWidth(setAnnotator),
                new Separator(),

                sectionLabel("Dataset"),
                labeledRow("Filter:", filterField),
                groupInfoLabel,
                fullWidth(button("Open group in grid",
                        "Load the filtered group's images into a single row of viewers (one per image). "
                                + "Do this before aligning.",
                        () -> run(new OpenImageGroupCommand(qupath, filterField.getText())))),
                new Separator(),

                sectionLabel("Alignment & sync"),
                referenceLabel,
                fullWidth(button("Set reference to current viewer",
                        "Pin the active viewer as the reference image; the others align to it. (Shortcut+Alt+F)",
                        () -> {
                            ViewerCommands.setReferenceToCurrent(qupath);
                            refreshReference();
                        })),
                labeledRow("Fit from:", sourceCombo),
                fullWidth(button("Place alignment points",
                        "Drop 3 draggable points; drag each onto a matching feature. Repeat on each image. (Shortcut+Alt+P)",
                        () -> run(new PlaceAlignmentPointsCommand(qupath)))),
                buttonRow(
                        button("Align & sync",
                                "Fit a similarity from the chosen points and keep all images navigating together; "
                                        + "the overlay uses the Overlay transform below. (Shortcut+Alt+Y)",
                                () -> {
                                    new AlignAndSyncCommand(qupath, transformCombo.getValue(), sourceCombo.getValue()).run();
                                    refreshAll();
                                }),
                        button("Stop sync", "Stop keeping the viewers in sync. (Shortcut+Alt+U)", () -> {
                            ViewerCommands.stopAlignedSync(qupath);
                            refreshAll();
                        })),
                fullWidth(button("Reset views",
                        "Clear rotation and zoom-to-fit; after stopping sync, returns images to their original state. (Shortcut+Alt+R)",
                        () -> ViewerCommands.resetViews(qupath))),
                statusLabel,
                new Separator(),

                sectionLabel("Landmarks"),
                fullWidth(button("Add landmark",
                        "Add one point at the view center; drag it into place. (Shortcut+Alt+L)",
                        () -> run(new AddLandmarkCommand(qupath)))),
                labeledRow("Grid N:", nSpinner),
                buttonRow(
                        button("Seed grid", "Place a regular grid of N points to drag onto landmarks. (Shortcut+Alt+G)",
                                () -> run(new SeedGridCommand(qupath, nSpinner.getValue()))),
                        button("Clear landmarks", "Remove all Anchor landmarks (LM-*).",
                                () -> run(new ClearLandmarksCommand(qupath)))),
                fullWidth(button("Load landmarks...",
                        "Import a landmark point set (GeoJSON) into the current image.",
                        () -> run(new LoadLandmarksCommand(qupath)))),
                buttonRow(
                        button("Export all...",
                                "Export every open image's landmarks to a chosen folder (one file per image). (Shortcut+Alt+E)",
                                () -> new ExportAllLandmarksCommand(qupath).run()),
                        button("Export view...",
                                "Export the current image's landmarks to GeoJSON (default) or CSV.",
                                () -> new ExportLandmarksCommand(qupath).run())),
                new Separator(),

                sectionLabel("Overlay"),
                labeledRow("Overlay transform:", transformCombo),
                fullWidth(overlayToggle),
                overlayInViewerCheck,
                imageSelectionBox);

        ScrollPane leftScroll = new ScrollPane(controls);
        leftScroll.setFitToWidth(true);

        // Right pane: the composite preview, sized by the divider.
        StackPane previewPane = new StackPane(overlayPreview);
        previewPane.setMinWidth(0);
        overlayPreview.fitWidthProperty().bind(previewPane.widthProperty().subtract(8));

        getItems().addAll(leftScroll, previewPane);
        setDividerPositions(1.0);   // overlay hidden by default -> preview pane collapsed

        refreshAll();
    }

    private void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Anchor help");
        alert.setHeaderText("Anchor workflow");
        if (qupath.getStage() != null)
            alert.initOwner(qupath.getStage());
        TextArea text = new TextArea(HELP_TEXT);
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefColumnCount(46);
        text.setPrefRowCount(28);
        alert.getDialogPane().setContent(text);
        alert.setResizable(true);
        alert.show();
    }

    private void run(Runnable command) {
        command.run();
        refreshAll();
    }

    private void refreshAll() {
        refreshSession();
        refreshReference();
        refreshStatus();
        overlayInViewerCheck.setSelected(ColorProjectionManager.getInstance().isInViewer());
    }

    private void refreshSession() {
        AnchorSession s = AnchorSession.getInstance();
        sessionLabel.setText("Annotator: " + (s.hasAnnotator() ? s.annotator() : "(not set)"));
    }

    private void refreshReference() {
        boolean pinned = AlignSyncManager.getInstance().getReferenceViewer() != null;
        referenceLabel.setText("Reference: " + (pinned
                ? "pinned (badged in its viewer)"
                : "auto (active viewer, else first image)"));
    }

    private void refreshStatus() {
        AlignSyncManager m = AlignSyncManager.getInstance();
        statusLabel.setText((m.isActive() ? "[synced] " : "[idle] ") + m.getLastStatus());
    }

    private void updateOverlayToggle() {
        overlayToggle.setText(ColorProjectionManager.getInstance().isPanelShown() ? "Hide overlay" : "Show overlay");
    }

    /** Show how many images are in the currently selected group. */
    private void updateGroupInfo() {
        String f = filterField.getText();
        if (f == null || f.isBlank()) {
            groupInfoLabel.setText("");
            return;
        }
        int n = OpenImageGroupCommand.groupSize(qupath, f);
        groupInfoLabel.setText(n == 0 ? "no images match this filter"
                : n + (n == 1 ? " image" : " images") + " match this filter");
    }

    /** Rebuild the per-image opacity sliders from the current participants (opacity 0 hides an image). */
    private void rebuildImageSelection() {
        imageSelectionBox.getChildren().clear();
        var participants = ColorProjectionManager.getInstance().getParticipants();
        if (participants.isEmpty())
            return;
        imageSelectionBox.getChildren().add(new Label("Overlay images (opacity):"));
        for (ColorProjectionManager.Participant p : participants) {
            Label name = new Label(p.name());
            name.setTooltip(new Tooltip(p.name()));   // full name on hover if ellipsized
            name.setMinWidth(0);                        // allow it to shrink and show the ellipsis
            name.setMaxWidth(Double.MAX_VALUE);
            java.awt.Color t = p.tint();                // color the name with this image's overlay tint
            name.setTextFill(javafx.scene.paint.Color.rgb(t.getRed(), t.getGreen(), t.getBlue()));
            name.setStyle("-fx-font-weight: bold;");
            Slider slider = new Slider(0, 1, p.opacity());
            slider.setPrefWidth(110);
            slider.setMinWidth(90);
            slider.setMaxWidth(120);
            slider.setTooltip(new Tooltip("Opacity of " + p.name() + " (0 hides it)."));
            slider.valueProperty().addListener((obs, o, v) ->
                    ColorProjectionManager.getInstance().setParticipantOpacity(p, v.doubleValue()));
            HBox row = new HBox(6, name, slider);
            HBox.setHgrow(name, Priority.ALWAYS);       // name takes remaining width, slider stays compact
            imageSelectionBox.getChildren().add(row);
        }
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        return l;
    }

    private static Node spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static Button button(String text, String tooltip, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        if (tooltip != null) {
            Tooltip t = new Tooltip(tooltip);
            t.setWrapText(true);
            t.setMaxWidth(320);
            b.setTooltip(t);
        }
        b.setOnAction(e -> action.run());
        return b;
    }

    private static Node fullWidth(Button b) {
        HBox box = new HBox(b);
        HBox.setHgrow(b, Priority.ALWAYS);
        return box;
    }

    private static HBox buttonRow(Button... buttons) {
        HBox row = new HBox(6, buttons);
        for (Button b : buttons)
            HBox.setHgrow(b, Priority.ALWAYS);
        return row;
    }

    private static HBox labeledRow(String labelText, Node control) {
        Label label = new Label(labelText);
        label.setMinWidth(70);
        HBox row = new HBox(6, label, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        if (control instanceof Control c)
            c.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
}
