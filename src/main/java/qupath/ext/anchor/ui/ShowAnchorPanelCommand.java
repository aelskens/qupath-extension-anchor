package qupath.ext.anchor.ui;

import javafx.scene.Scene;
import javafx.stage.Stage;

import qupath.lib.gui.QuPathGUI;

/**
 * Opens (or focuses) the Anchor control panel in its own always-available window.
 */
public class ShowAnchorPanelCommand implements Runnable {

    private final QuPathGUI qupath;
    private Stage stage;

    public ShowAnchorPanelCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Anchor");
            if (qupath.getStage() != null)
                stage.initOwner(qupath.getStage());
            stage.setScene(new Scene(new AnchorPanel(qupath)));
        }
        stage.show();
        stage.toFront();
    }
}
