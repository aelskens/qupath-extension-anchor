package qupath.ext.anchor.viewer;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.anchor.transform.LandmarkTransform;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
 * Keeps two or more viewers in visual registration through fitted transforms, coupling them via a
 * shared reference frame. Each viewer has a {@link LandmarkTransform} mapping the reference-image
 * pixel space into that viewer's image space (the reference viewer uses the identity). Moving any
 * viewer maps its camera back into the reference frame and forward into every other viewer.
 * <p>
 * The live viewer sync is always driven by a <b>linear</b> transform (rigid/similarity/affine): a
 * live viewer cannot be elastically warped, so a nonlinear fit (TPS) is kept out of the sync path.
 * A separate set of transforms (which <i>may</i> be nonlinear/TPS) is held only for the elastic
 * overlay compositor; see {@link #getOverlaySecondaryAlignments()}. This keeps the viewers in the
 * exact same registration whether the user renders the overlay as affine or TPS.
 */
public class AlignedSyncController implements QuPathViewerListener {

    private static final Logger logger = LoggerFactory.getLogger(AlignedSyncController.class);

    /**
     * Sign of the rotation coupling. QuPath's viewer transform is translate . scale . rotate(+rot) .
     * translate, so reproducing the fitted transform requires rot_target = rot_reference -
     * rotationOf(A_target); that is, this sign must be -1. (With +1 each viewer was rotated by about
     * twice the fitted angle, so the viewers were badly misaligned while the overlay, which applies
     * the transform directly, stayed correct.)
     */
    private static final double ROTATION_SIGN = -1.0;

    private final List<QuPathViewer> viewers = new ArrayList<>();
    /** Reference-to-image transforms driving the live sync; always linear. */
    private final Map<QuPathViewer, LandmarkTransform> transforms = new IdentityHashMap<>();
    /** Reference-to-image transforms for the overlay only; may be nonlinear (TPS). */
    private final Map<QuPathViewer, LandmarkTransform> overlayTransforms = new IdentityHashMap<>();

    /** Last known center in reference-image coordinates (the linearization point for nonlinear warps). */
    private Point2D referenceCenter;

    private boolean applying = false;
    private boolean rotationSyncEnabled = true;

    public AlignedSyncController(List<ViewerAlignment> alignments) {
        this(alignments, alignments);
    }

    /**
     * @param syncAlignments    reference-to-image transforms driving the live sync (must be linear)
     * @param overlayAlignments reference-to-image transforms for the overlay (may be nonlinear/TPS);
     *                          if null/empty, the sync transforms are reused
     */
    public AlignedSyncController(List<ViewerAlignment> syncAlignments, List<ViewerAlignment> overlayAlignments) {
        if (syncAlignments == null || syncAlignments.size() < 2)
            throw new IllegalArgumentException("Aligned sync needs at least two viewers.");
        for (ViewerAlignment a : syncAlignments) {
            viewers.add(a.viewer());
            transforms.put(a.viewer(), a.referenceToViewer());
        }
        List<ViewerAlignment> overlay = (overlayAlignments == null || overlayAlignments.isEmpty())
                ? syncAlignments : overlayAlignments;
        for (ViewerAlignment a : overlay)
            overlayTransforms.put(a.viewer(), a.referenceToViewer());
        QuPathViewer reference = viewers.get(0);
        referenceCenter = new Point2D.Double(reference.getCenterPixelX(), reference.getCenterPixelY());
    }

    /** The reference viewer (identity transform); all others align to it. */
    public QuPathViewer getReferenceViewer() {
        return viewers.get(0);
    }

    /** The non-reference viewers with their linear reference-to-image transforms (the sync transforms). */
    public List<ViewerAlignment> getSecondaryAlignments() {
        List<ViewerAlignment> result = new ArrayList<>();
        for (int i = 1; i < viewers.size(); i++) {
            QuPathViewer v = viewers.get(i);
            result.add(new ViewerAlignment(v, transforms.get(v)));
        }
        return result;
    }

    /**
     * The overlay transform (reference pixels -&gt; that viewer's image) for any participating viewer:
     * the identity for the reference, the fitted (possibly nonlinear) transform for a secondary, or
     * {@code null} if the viewer is not part of this alignment.
     */
    public LandmarkTransform getOverlayTransform(QuPathViewer viewer) {
        if (viewer == getReferenceViewer())
            return ViewerAlignment.identity();
        return overlayTransforms.get(viewer);
    }

    /**
     * The non-reference viewers with the transforms used for the elastic overlay compositor. These
     * may be nonlinear (TPS) even though the live sync uses only the linear transforms above.
     */
    public List<ViewerAlignment> getOverlaySecondaryAlignments() {
        List<ViewerAlignment> result = new ArrayList<>();
        for (int i = 1; i < viewers.size(); i++) {
            QuPathViewer v = viewers.get(i);
            LandmarkTransform t = overlayTransforms.getOrDefault(v, transforms.get(v));
            result.add(new ViewerAlignment(v, t));
        }
        return result;
    }

    public void start() {
        for (QuPathViewer v : viewers)
            v.addViewerListener(this);
        syncFrom(viewers.get(0));
        logger.info("Aligned sync started across {} viewers.", viewers.size());
    }

    public void stop() {
        for (QuPathViewer v : viewers)
            v.removeViewerListener(this);
        logger.info("Aligned sync stopped.");
    }

    public void setRotationSyncEnabled(boolean enabled) {
        this.rotationSyncEnabled = enabled;
    }

    /** Clear the reference viewer's rotation, zoom-to-fit it, then propagate to the others. */
    public void fitReferenceAndSync() {
        applying = true;
        try {
            QuPathViewer reference = viewers.get(0);
            reference.setRotation(0);
            reference.zoomToFit();
            doSync(reference);
        } finally {
            Platform.runLater(() -> applying = false);
        }
    }

    @Override
    public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
        if (applying)
            return;
        if (transforms.containsKey(viewer))
            syncFrom(viewer);
    }

    private void syncFrom(QuPathViewer source) {
        applying = true;
        try {
            doSync(source);
        } finally {
            Platform.runLater(() -> applying = false);
        }
    }

    /** Map {@code source}'s camera into the reference frame, then out to every other viewer. */
    private void doSync(QuPathViewer source) {
        try {
            Point2D sourceCenter = new Point2D.Double(source.getCenterPixelX(), source.getCenterPixelY());
            LandmarkTransform sourceTransform = transforms.get(source);

            // Reference-frame center: identity for the reference viewer, else invert the source's
            // local affine (linearized at the last known reference center).
            Point2D r;
            if (source == viewers.get(0)) {
                r = sourceCenter;
            } else {
                AffineTransform sourceLocal = sourceTransform.localAffine(referenceCenter);
                r = sourceLocal.createInverse().transform(sourceCenter, null);
            }
            referenceCenter = r;

            AffineTransform sourceLocalAtR = sourceTransform.localAffine(r);
            double refRotation = source.getRotation() - ROTATION_SIGN * rotationOf(sourceLocalAtR);
            double refDownsample = source.getDownsampleFactor() / scaleOf(sourceLocalAtR);

            for (QuPathViewer target : viewers) {
                if (target == source)
                    continue;
                LandmarkTransform targetTransform = transforms.get(target);
                Point2D targetCenter = targetTransform.apply(r);
                AffineTransform targetLocal = targetTransform.localAffine(r);
                target.setDownsampleFactor(refDownsample * scaleOf(targetLocal),
                        targetCenter.getX(), targetCenter.getY(), false);
                if (rotationSyncEnabled)
                    target.setRotation(refRotation + ROTATION_SIGN * rotationOf(targetLocal));
                target.setCenterPixelLocation(targetCenter.getX(), targetCenter.getY());
            }
        } catch (NoninvertibleTransformException e) {
            logger.warn("Skipping sync: transform not invertible at the current view", e);
        }
    }

    static double scaleOf(AffineTransform t) {
        return Math.sqrt(Math.abs(t.getDeterminant()));
    }

    static double rotationOf(AffineTransform t) {
        return Math.atan2(t.getShearY(), t.getScaleX());
    }

    @Override
    public void viewerClosed(QuPathViewer viewer) {
        if (transforms.containsKey(viewer))
            stop();
    }

    @Override
    public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> oldData,
                                 ImageData<BufferedImage> newData) {
        if (transforms.containsKey(viewer)) {
            logger.info("Image changed in a synced viewer; stopping aligned sync.");
            stop();
        }
    }

    @Override
    public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
        // no-op
    }
}
