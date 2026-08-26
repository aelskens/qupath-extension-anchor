package qupath.ext.anchor.overlay;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import qupath.ext.anchor.model.ImageNames;
import qupath.ext.anchor.model.Landmarks;
import qupath.ext.anchor.transform.LandmarkTransform;
import qupath.ext.anchor.transform.ReframedTransform;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.ext.anchor.viewer.AlignedSyncController;
import qupath.ext.anchor.viewer.ViewerAlignment;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
 * Builds a false-color composite of the aligned images and shows it either in the Anchor panel
 * preview and/or directly in a chosen viewer (in place). The two outputs are independent: the panel
 * preview and the in-viewer overlay can each be on or off. Each image has its own opacity (0 hides
 * it). The composite is rendered in the <b>frame</b> of whichever viewer is being targeted (the
 * reference viewer for panel-only mode, or the selected viewer when the in-viewer overlay is on), so
 * "show in viewer" works for any viewer, not just the reference. Rendering covers the current view
 * region (plus a small margin, so small pans do not reveal the bare image underneath) and runs on a
 * background thread (coalesced) so panning/zooming stays responsive.
 */
public final class ColorProjectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ColorProjectionManager.class);
    private static final ColorProjectionManager INSTANCE = new ColorProjectionManager();

    /** Distinct high-contrast tints, assigned per participating image. */
    private static final Color[] TINTS = {
            Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.GREEN, Color.ORANGE, Color.RED
    };

    /** One image participating in the composite. */
    public static final class Participant {
        private final QuPathViewer viewer;
        private final LandmarkTransform refToImage;
        private final Color tint;
        private final String name;
        private final boolean invert;
        private volatile double opacity = 0.6;

        Participant(QuPathViewer viewer, LandmarkTransform refToImage, Color tint, String name, boolean invert) {
            this.viewer = viewer;
            this.refToImage = refToImage;
            this.tint = tint;
            this.name = name;
            this.invert = invert;
        }

        public String name() { return name; }
        public Color tint() { return tint; }
        public double opacity() { return opacity; }
    }

    private record RenderRequest(qupath.lib.gui.images.stores.DefaultImageRegionStore store,
                                 double cx, double cy, double ds, double rot, int width, int height,
                                 List<CompositeRenderer.Layer> layers, List<CompositeRenderer.Marker> markers) {}

    /**
     * Area rendered around the viewport (as a multiple of the viewport). Generous, so that while the
     * user moves, the in-viewer overlay just re-places the last composite (see ColorProjectionOverlay)
     * and only re-renders once movement pauses, instead of re-rendering on every navigation event.
     */
    private static final double VIEW_MARGIN = 1.6;
    /** Quiet period after the last navigation event before a fresh composite is rendered (ms). */
    private static final double RENDER_DEBOUNCE_MS = 120;

    private final List<Participant> participants = new ArrayList<>();
    private final Map<ImageData<BufferedImage>, ImageDisplay> displayCache = new HashMap<>();

    private volatile boolean showPanel = false;     // draw the composite in the panel preview
    private volatile boolean showInViewer = false;  // draw the composite directly in the target viewer
    private volatile ImageView previewView;
    private volatile QuPathViewer referenceViewer;  // set from the active controller
    private QuPathViewer targetViewer;              // the viewer chosen for the in-viewer overlay
    private ColorProjectionOverlay viewerOverlay;   // the in-viewer overlay instance
    private QuPathViewer overlayViewer;             // the viewer viewerOverlay is attached to
    private QuPathViewer listenedViewer;            // the viewer previewListener is attached to
    private Runnable onParticipantsChanged;
    private PauseTransition renderDebounce;          // coalesces navigation events into one render on pause

    private volatile RenderRequest pending;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final ExecutorService renderExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "anchor-composite");
        t.setDaemon(true);
        return t;
    });

    private final QuPathViewerListener previewListener = new QuPathViewerListener() {
        @Override
        public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
            // While the user is moving, the in-viewer overlay re-places the last composite itself;
            // only render a fresh one once movement pauses (debounced), which is the expensive part.
            scheduleRenderDebounced();
        }

        @Override
        public void viewerClosed(QuPathViewer viewer) {}

        @Override
        public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> oldData,
                                     ImageData<BufferedImage> newData) {
            scheduleRender();
        }

        @Override
        public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}
    };

    private ColorProjectionManager() {}

    public static ColorProjectionManager getInstance() {
        return INSTANCE;
    }

    /** Whether the composite is shown in the panel preview. */
    public synchronized boolean isPanelShown() {
        return showPanel;
    }

    public synchronized List<Participant> getParticipants() {
        return new ArrayList<>(participants);
    }

    /** Show/hide the composite in the panel preview (independent of the in-viewer overlay). */
    public synchronized void setPanelShown(QuPathGUI qupath, boolean shown) {
        this.showPanel = shown;
        rebuild(qupath);
    }

    /** Whether the composite is drawn directly in a viewer. */
    public synchronized boolean isInViewer() {
        return showInViewer;
    }

    /** Show/hide the composite in the selected viewer (in place), independent of the panel preview. */
    public synchronized void setInViewer(QuPathGUI qupath, boolean inViewer) {
        this.showInViewer = inViewer;
        if (inViewer)
            targetViewer = qupath.getViewer();   // the currently selected/active viewer
        rebuild(qupath);
    }

    /** Toggle the in-viewer overlay on the currently selected viewer (menu/shortcut entry point). */
    public synchronized void toggleInViewer(QuPathGUI qupath) {
        setInViewer(qupath, !showInViewer);
    }

    public synchronized void setParticipantOpacity(Participant participant, double opacity) {
        participant.opacity = Math.max(0, Math.min(1, opacity));
        scheduleRender();
    }

    /** Call when aligned sync starts/stops so the composite follows the current alignment. */
    public synchronized void onAlignmentChanged(QuPathGUI qupath) {
        rebuild(qupath);
    }

    /** Register the panel's preview image view (or {@code null} to clear). */
    public synchronized void setPreviewView(ImageView view) {
        this.previewView = view;
        scheduleRender();
    }

    /** Register a callback invoked (on the FX thread) when the participant list changes. */
    public synchronized void setOnParticipantsChanged(Runnable callback) {
        this.onParticipantsChanged = callback;
    }

    private void rebuild(QuPathGUI qupath) {
        participants.clear();
        AlignedSyncController controller = AlignSyncManager.getInstance().getActiveController();
        if (controller != null) {
            referenceViewer = controller.getReferenceViewer();
            addParticipant(referenceViewer, ViewerAlignment.identity(), 0);
            int i = 1;
            for (ViewerAlignment sa : controller.getOverlaySecondaryAlignments()) {
                addParticipant(sa.viewer(), sa.referenceToViewer(), i);
                i++;
            }
        } else {
            referenceViewer = null;
        }
        if (onParticipantsChanged != null)
            Platform.runLater(onParticipantsChanged);
        scheduleRender();   // captureAndSubmit re-binds the listener/overlay to the current frame
    }

    private void addParticipant(QuPathViewer viewer, LandmarkTransform refToImage, int index) {
        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData == null)
            return;
        Color tint = TINTS[index % TINTS.length];
        String name = ImageNames.fileName(imageData.getServer());
        // Invert bright-background images (brightfield/RGB, e.g. H&E) so tissue lights up; leave
        // fluorescence (dark background, bright signal) as-is.
        boolean invert = imageData.getServer().isRGB();
        participants.add(new Participant(viewer, refToImage, tint, name, invert));

        if (!displayCache.containsKey(imageData)) {
            try {
                displayCache.put(imageData, ImageDisplay.create(imageData));
            } catch (IOException e) {
                logger.warn("Could not create ImageDisplay for a composite layer", e);
            }
        }
    }

    /** Render promptly (used for enable/opacity/alignment changes); cancels any pending debounce. */
    private void scheduleRender() {
        Platform.runLater(() -> {
            if (renderDebounce != null)
                renderDebounce.stop();
            captureAndSubmit();
        });
    }

    /** Render after a short quiet period, so continuous navigation coalesces into a single render. */
    private void scheduleRenderDebounced() {
        Platform.runLater(() -> {
            if (renderDebounce == null) {
                renderDebounce = new PauseTransition(Duration.millis(RENDER_DEBOUNCE_MS));
                renderDebounce.setOnFinished(e -> captureAndSubmit());
            }
            renderDebounce.playFromStart();
        });
    }

    /**
     * The viewer whose frame the composite is rendered in: the selected target when the in-viewer
     * overlay is on and that viewer participates, otherwise the reference viewer.
     */
    private QuPathViewer chooseFrameViewer(AlignedSyncController controller) {
        if (controller == null)
            return null;
        if (showInViewer && targetViewer != null && controller.getOverlayTransform(targetViewer) != null)
            return targetViewer;
        return controller.getReferenceViewer();
    }

    /** Attach the region listener and the in-viewer overlay to the current frame viewer (FX thread). */
    private void updateFrameBindings(QuPathViewer frame) {
        QuPathViewer wantListen = (frame != null && (showPanel || showInViewer)) ? frame : null;
        if (wantListen != listenedViewer) {
            if (listenedViewer != null)
                listenedViewer.removeViewerListener(previewListener);
            if (wantListen != null)
                wantListen.addViewerListener(previewListener);
            listenedViewer = wantListen;
        }
        QuPathViewer wantOverlay = (showInViewer && frame != null) ? frame : null;
        if (wantOverlay != overlayViewer) {
            if (viewerOverlay != null && overlayViewer != null)
                overlayViewer.getCustomOverlayLayers().remove(viewerOverlay);
            viewerOverlay = null;
            overlayViewer = null;
            if (wantOverlay != null) {
                viewerOverlay = new ColorProjectionOverlay(wantOverlay);
                overlayViewer = wantOverlay;
                wantOverlay.getCustomOverlayLayers().add(viewerOverlay);
            }
        }
    }

    /** On the FX thread: bind to the current frame, snapshot the render inputs, hand off to the renderer. */
    private synchronized void captureAndSubmit() {
        AlignedSyncController controller = AlignSyncManager.getInstance().getActiveController();
        QuPathViewer frame = chooseFrameViewer(controller);
        updateFrameBindings(frame);

        RenderRequest req = capture(controller, frame);
        pending = req;
        if (req == null) {
            if (previewView != null)
                previewView.setImage(null);
            if (viewerOverlay != null) {   // nothing to show: clear the in-viewer composite
                viewerOverlay.clear();
                if (overlayViewer != null)
                    overlayViewer.repaint();
            }
            return;
        }
        if (scheduled.compareAndSet(false, true))
            renderExec.submit(this::renderTask);
    }

    /** Snapshot the render inputs for the composite in {@code frame}'s coordinate space. */
    private RenderRequest capture(AlignedSyncController controller, QuPathViewer frame) {
        boolean toPreview = showPanel && previewView != null;
        if (!toPreview && !showInViewer)         // nowhere to show the composite
            return null;
        if (controller == null || frame == null || frame.getImageData() == null)
            return null;
        LandmarkTransform refToFrame = controller.getOverlayTransform(frame);
        if (refToFrame == null)
            return null;
        boolean frameIsReference = frame == controller.getReferenceViewer();

        // Each layer's transform maps the frame viewer's pixels to that image; for the reference frame
        // this is just refToImage (affine fast path preserved), otherwise it is reframed via the target.
        List<CompositeRenderer.Layer> layers = new ArrayList<>();
        for (Participant p : participants) {
            if (p.opacity <= 0)
                continue;
            ImageData<BufferedImage> data = p.viewer.getImageData();
            ImageDisplay display = data == null ? null : displayCache.get(data);
            if (display == null)
                continue;
            LandmarkTransform frameToImage = frameIsReference
                    ? p.refToImage
                    : new ReframedTransform(refToFrame, p.refToImage);
            layers.add(new CompositeRenderer.Layer(data.getServer(), frameToImage, display,
                    p.tint, p.invert, p.opacity, 0, 0));
        }
        if (layers.isEmpty())
            return null;

        double viewW = frame.getView().getWidth();
        double viewH = frame.getView().getHeight();
        int w = (int) Math.round(viewW * VIEW_MARGIN);
        int h = (int) Math.round(viewH * VIEW_MARGIN);
        if (w <= 0 || h <= 0)
            return null;

        // Each shown image's landmarks, mapped into the frame, colored by source image. A correct
        // warp makes each matched pair's two dots coincide everywhere in the view.
        List<CompositeRenderer.Marker> markers = new ArrayList<>();
        for (Participant p : participants) {
            if (p.opacity <= 0)
                continue;
            ImageData<BufferedImage> data = p.viewer.getImageData();
            if (data == null)
                continue;
            for (PathObject o : data.getHierarchy().getAnnotationObjects()) {
                if (!Landmarks.isLandmark(o))
                    continue;
                Point2D pt = Landmarks.getPoint(o);
                if (pt == null)
                    continue;
                Point2D refPt = p.refToImage.applyInverse(pt);   // image -> reference frame
                if (refPt == null)
                    continue;
                Point2D framePt = refToFrame.apply(refPt);       // reference -> frame
                if (framePt != null)
                    markers.add(new CompositeRenderer.Marker(framePt.getX(), framePt.getY(), p.tint));
            }
        }

        return new RenderRequest(frame.getImageRegionStore(), frame.getCenterPixelX(), frame.getCenterPixelY(),
                frame.getDownsampleFactor(), frame.getRotation(), w, h, layers, markers);
    }

    /** On the background thread: render the latest request, post to preview/viewer, coalesce newer ones. */
    private void renderTask() {
        RenderRequest req = pending;
        if (req != null) {
            try {
                BufferedImage img = CompositeRenderer.render(req.store(), req.cx(), req.cy(), req.ds(),
                        req.rot(), req.width(), req.height(), req.layers(), req.markers());
                // Transform the composite was rendered with (frame pixels -> device at render time).
                AffineTransform base = CompositeRenderer.viewerTransform(req.width(), req.height(),
                        req.cx(), req.cy(), req.ds(), req.rot());
                Platform.runLater(() -> {
                    ImageView iv = previewView;
                    if (iv != null && showPanel)
                        iv.setImage(toFX(img));
                    ColorProjectionOverlay ov = viewerOverlay;
                    if (ov != null && overlayViewer != null && showInViewer) {
                        ov.setComposite(img, base);
                        overlayViewer.repaint();
                    }
                });
            } catch (RuntimeException e) {
                logger.warn("Composite render failed", e);
            }
        }
        scheduled.set(false);
        if (pending != req && pending != null && scheduled.compareAndSet(false, true))
            renderExec.submit(this::renderTask);
    }

    private static WritableImage toFX(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        WritableImage fx = new WritableImage(w, h);
        fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return fx;
    }
}
