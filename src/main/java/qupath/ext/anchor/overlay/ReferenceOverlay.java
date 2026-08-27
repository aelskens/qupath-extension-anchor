package qupath.ext.anchor.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javafx.scene.layout.Pane;

import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

/**
 * Draws a "REFERENCE" badge pinned to the bottom-center of the viewport, marking the image that
 * holds the reference frame for aligned sync.
 * <p>
 * Painted in screen/viewport pixels (the graphics transform is reset to identity and the badge is
 * placed using the viewer component's size), so it stays fixed regardless of pan, zoom, or rotation.
 */
public class ReferenceOverlay extends AbstractOverlay {

    private static final String TEXT = "REFERENCE";
    private static final int PAD = 6;

    private final QuPathViewer viewer;

    public ReferenceOverlay(QuPathViewer viewer) {
        super(viewer.getOverlayOptions());
        this.viewer = viewer;
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
                             ImageData<BufferedImage> imageData, boolean paintCompletely) {
        Pane view = viewer.getView();
        if (view == null)
            return;
        double width = view.getWidth();
        double height = view.getHeight();
        if (width <= 0 || height <= 0)
            return;

        Graphics2D g = (Graphics2D) g2d.create();
        try {
            // Draw in viewport pixels (identity transform) so the badge is screen-fixed, centered at the bottom.
            g.setTransform(new AffineTransform());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g.getFontMetrics();

            int textWidth = fm.stringWidth(TEXT);
            int x = (int) Math.round((width - textWidth) / 2.0);
            int baseline = (int) Math.round(height) - PAD - 4;

            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x - 4, baseline - fm.getAscent() - 2, textWidth + 8, fm.getAscent() + fm.getDescent() + 4);
            g.setColor(Color.WHITE);
            g.drawString(TEXT, x, baseline);
        } finally {
            g.dispose();
        }
    }
}
