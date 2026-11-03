package qupath.ext.anchor.viewer;

import java.awt.geom.Point2D;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Remembers the last mouse position over QuPath's main window, so keyboard-triggered commands (e.g.
 * "Add landmark" via its accelerator) can act where the cursor is pointing.
 * <p>
 * Implemented as a scene-level event filter rather than per-viewer listeners, so viewers created later
 * (multi-view grid resizes, opening an image group) are covered automatically. The position is cleared
 * when the cursor leaves the window. Viewers detached into their own window are not tracked; commands
 * fall back to the view center for them.
 */
public final class CursorTracker {

    private static final CursorTracker INSTANCE = new CursorTracker();

    /** Last cursor position in scene coordinates, or {@code null} if outside the main window. */
    private Point2D scenePosition;
    private Scene scene;

    private CursorTracker() {}

    public static CursorTracker getInstance() {
        return INSTANCE;
    }

    /** Attach to QuPath's main window scene (now, or as soon as it has one). Idempotent. */
    public void install(QuPathGUI qupath) {
        var stage = qupath.getStage();
        if (stage == null)
            return;
        if (stage.getScene() != null)
            attach(stage.getScene());
        stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null)
                attach(newScene);
        });
    }

    private void attach(Scene newScene) {
        if (newScene == scene)
            return;
        scene = newScene;
        newScene.addEventFilter(MouseEvent.MOUSE_MOVED, this::record);
        newScene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::record);
        // MOUSE_EXITED (not EXITED_TARGET) only fires when the scene itself is exited.
        newScene.addEventFilter(MouseEvent.MOUSE_EXITED, e -> scenePosition = null);
    }

    private void record(MouseEvent e) {
        scenePosition = new Point2D.Double(e.getSceneX(), e.getSceneY());
    }

    /**
     * The cursor location in {@code viewer}'s full-resolution image pixels, or {@code null} if the
     * cursor is not currently over that viewer (or is outside the image).
     */
    public Point2D imagePointUnderCursor(QuPathViewer viewer) {
        if (viewer == null || viewer.getImageData() == null || scenePosition == null)
            return null;
        Node view = viewer.getView();
        if (view == null || view.getScene() != scene)
            return null;
        javafx.geometry.Point2D local = view.sceneToLocal(scenePosition.getX(), scenePosition.getY());
        if (local == null)
            return null;
        Bounds bounds = view.getLayoutBounds();
        if (!bounds.contains(local))
            return null;
        Point2D image = viewer.componentPointToImagePoint(local.getX(), local.getY(), null, false);
        var server = viewer.getServer();
        if (image == null || server == null)
            return null;
        if (image.getX() < 0 || image.getY() < 0 || image.getX() > server.getWidth() || image.getY() > server.getHeight())
            return null;
        return image;
    }
}
