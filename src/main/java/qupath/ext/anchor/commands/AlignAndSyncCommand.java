package qupath.ext.anchor.commands;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.anchor.model.Landmarks;
import qupath.ext.anchor.model.PointSource;
import qupath.ext.anchor.overlay.ColorProjectionManager;
import qupath.ext.anchor.transform.CorrespondenceSet;
import qupath.ext.anchor.transform.IdPoint;
import qupath.ext.anchor.transform.TransformFitResult;
import qupath.ext.anchor.transform.TransformFitter;
import qupath.ext.anchor.transform.TransformType;
import qupath.ext.anchor.viewer.AlignSyncManager;
import qupath.ext.anchor.viewer.AlignedSyncController;
import qupath.ext.anchor.viewer.ReferenceIndicator;
import qupath.ext.anchor.viewer.ViewerAlignment;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;

/**
 * Fit a transform from a chosen subset of landmarks on every open image that has them, and start
 * aligned view sync across all of them.
 * <p>
 * The reference is the user-chosen reference viewer if set and usable, otherwise the active viewer,
 * otherwise the first image with points. The chosen reference is remembered and badged, so it stays
 * stable across resets and alignments.
 */
public class AlignAndSyncCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AlignAndSyncCommand.class);
    private static final String TITLE = "Align & sync viewers";

    /** The live viewer sync always uses a similarity (all a raw viewer can honestly reproduce). */
    private static final TransformType SYNC_TYPE = TransformType.SIMILARITY;

    private final QuPathGUI qupath;
    private final TransformType overlayType;
    private final PointSource pointSource;
    private final boolean quiet;

    public AlignAndSyncCommand(QuPathGUI qupath) {
        this(qupath, TransformType.AFFINE, PointSource.MANUAL);
    }

    public AlignAndSyncCommand(QuPathGUI qupath, TransformType overlayType, PointSource pointSource) {
        this(qupath, overlayType, pointSource, false);
    }

    /**
     * @param overlayType the transform used for the overlay only (the sync is always a similarity)
     * @param quiet       suppress the success notification (used when re-fitting just the overlay)
     */
    public AlignAndSyncCommand(QuPathGUI qupath, TransformType overlayType, PointSource pointSource, boolean quiet) {
        this.qupath = qupath;
        this.overlayType = overlayType == null ? TransformType.AFFINE : overlayType;
        this.pointSource = pointSource == null ? PointSource.MANUAL : pointSource;
        this.quiet = quiet;
    }

    @Override
    public void run() {
        List<QuPathViewer> imaged = new ArrayList<>();
        for (QuPathViewer v : qupath.getAllViewers()) {
            if (v.getImageData() != null)
                imaged.add(v);
        }
        if (imaged.size() < 2) {
            Dialogs.showErrorMessage(TITLE, "Open at least two images in separate viewers (View > Multi-view).");
            return;
        }

        QuPathViewer reference = chooseReference(imaged);
        if (reference == null) {
            Dialogs.showErrorMessage(TITLE, String.format(
                    "No open image has usable points for '%s'. Place points first.", pointSource.label()));
            return;
        }
        List<IdPoint> referencePoints = collectPoints(reference, pointSource);

        // The sync is always a similarity (all a raw viewer can honestly reproduce); the chosen
        // transform (rigid/similarity/affine/TPS) is applied to the overlay only.
        int minPoints = Math.max(SYNC_TYPE.hardMinimumPoints(), overlayType.hardMinimumPoints());
        List<ViewerAlignment> syncAlignments = new ArrayList<>();
        List<ViewerAlignment> overlayAlignments = new ArrayList<>();
        syncAlignments.add(ViewerAlignment.reference(reference));
        overlayAlignments.add(ViewerAlignment.reference(reference));
        List<String> skipped = new ArrayList<>();
        double worstRms = 0;

        for (QuPathViewer v : imaged) {
            if (v == reference)
                continue;
            CorrespondenceSet cs;
            try {
                cs = CorrespondenceSet.resolve(referencePoints, collectPoints(v, pointSource));
            } catch (IllegalArgumentException e) {
                skipped.add("one image (" + e.getMessage() + ")");
                continue;
            }
            if (cs.size() < minPoints) {
                skipped.add("one image (only " + cs.size() + " matched points)");
                continue;
            }
            try {
                TransformFitResult syncFit = TransformFitter.fit(SYNC_TYPE, cs);
                // Reuse the similarity fit for the overlay when that is also the chosen overlay type.
                var overlayTransform = overlayType == SYNC_TYPE
                        ? syncFit.transform()
                        : TransformFitter.fit(overlayType, cs).transform();
                syncAlignments.add(new ViewerAlignment(v, syncFit.transform()));
                overlayAlignments.add(new ViewerAlignment(v, overlayTransform));
                worstRms = Math.max(worstRms, syncFit.rmsErrorPixels());
            } catch (RuntimeException e) {
                skipped.add("one image (" + e.getMessage() + ")");
            }
        }

        if (syncAlignments.size() < 2) {
            Dialogs.showErrorMessage(TITLE, String.format(
                    "No other image shares at least %d matched '%s' points with the reference.",
                    minPoints, pointSource.label()));
            return;
        }

        // Turn off QuPath's built-in sync so the two mechanisms don't fight over the viewers.
        qupath.getViewerManager().setSynchronizeViewers(false);

        AlignedSyncController controller = new AlignedSyncController(syncAlignments, overlayAlignments);
        AlignSyncManager.getInstance().start(controller);
        ReferenceIndicator.setReference(qupath, reference);
        ColorProjectionManager.getInstance().onAlignmentChanged(qupath);

        // The viewers sync via similarity; the chosen transform applies to the overlay only. The RMS
        // reported is the similarity (sync) fit, the meaningful residual for the viewers.
        String status = String.format("Synced %d images (similarity sync + %s overlay, %s). Worst sync RMS = %.1f px.",
                syncAlignments.size(), overlayType, pointSource.label(), worstRms);
        AlignSyncManager.getInstance().setLastStatus(status);
        logger.info("{}{}", status, skipped.isEmpty() ? "" : " Skipped: " + skipped);
        if (!quiet)
            Dialogs.showInfoNotification(TITLE,
                    status + (skipped.isEmpty() ? "" : "\nSkipped " + skipped.size() + " image(s)."));
    }

    /** Prefer the user's chosen reference, then the active viewer, then the first image with points. */
    private QuPathViewer chooseReference(List<QuPathViewer> imaged) {
        QuPathViewer stored = AlignSyncManager.getInstance().getReferenceViewer();
        if (stored != null && imaged.contains(stored) && !collectPoints(stored, pointSource).isEmpty())
            return stored;
        QuPathViewer active = qupath.getViewer();
        if (active != null && imaged.contains(active) && !collectPoints(active, pointSource).isEmpty())
            return active;
        for (QuPathViewer v : imaged) {
            if (!collectPoints(v, pointSource).isEmpty())
                return v;
        }
        return null;
    }

    /** Collect id-tagged points from a viewer's image for the given source. */
    private static List<IdPoint> collectPoints(QuPathViewer viewer, PointSource source) {
        List<IdPoint> points = new ArrayList<>();
        var imageData = viewer.getImageData();
        if (imageData == null)
            return points;
        for (PathObject o : imageData.getHierarchy().getAnnotationObjects()) {
            if (!source.includes(o))
                continue;
            var id = Landmarks.getId(o);
            Point2D p = Landmarks.getPoint(o);
            if (id.isPresent() && p != null)
                points.add(new IdPoint(id.getAsInt(), p));
        }
        return points;
    }
}
