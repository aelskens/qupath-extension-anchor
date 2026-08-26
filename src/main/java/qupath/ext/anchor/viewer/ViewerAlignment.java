package qupath.ext.anchor.viewer;

import qupath.ext.anchor.transform.AffineLinearTransform;
import qupath.ext.anchor.transform.LandmarkTransform;
import qupath.ext.anchor.transform.TransformType;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Pairs a viewer with the transform mapping the shared reference frame into that viewer's image space.
 * The reference viewer itself uses the identity transform. The transform may be linear (affine /
 * similarity / rigid) or nonlinear (TPS); consumers use {@link LandmarkTransform#localAffine} to work
 * with it in the affine-based sync/overlay pipeline.
 *
 * @param viewer            the viewer
 * @param referenceToViewer transform mapping reference-image pixels to this viewer's image pixels
 */
public record ViewerAlignment(QuPathViewer viewer, LandmarkTransform referenceToViewer) {

    public ViewerAlignment {
        if (viewer == null || referenceToViewer == null)
            throw new IllegalArgumentException("viewer and referenceToViewer are required");
    }

    /** Convenience for the reference viewer (identity transform). */
    public static ViewerAlignment reference(QuPathViewer viewer) {
        return new ViewerAlignment(viewer, identity());
    }

    /** The identity transform (reference maps to itself). */
    public static LandmarkTransform identity() {
        return new AffineLinearTransform(TransformType.AFFINE, 1, 0, 0, 1, 0, 0);
    }
}
