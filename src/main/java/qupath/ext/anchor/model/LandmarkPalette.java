package qupath.ext.anchor.model;

import java.awt.Color;

import qupath.lib.common.ColorTools;

/**
 * Deterministic categorical color keyed by landmark id.
 * <p>
 * Uses golden-angle hue rotation: deterministic, unbounded in N, with adjacent ids ~137.5 degrees
 * apart in hue so consecutively-numbered landmarks are easy to tell apart. Fixed high saturation and
 * value keep colors vivid on both H&amp;E and IF tissue and never near-white.
 */
public final class LandmarkPalette {

    private LandmarkPalette() {}

    /** Golden-ratio conjugate; the per-id hue step. */
    private static final double GOLDEN_CONJUGATE = 0.61803398875;

    private static final float SATURATION = 0.85f;
    private static final float BRIGHTNESS = 0.95f;

    /** Packed RGB color (as used by {@code PathObject.setColor(Integer)}) for a 1-based landmark id. */
    public static int colorForId(int landmarkId) {
        double hue = ((landmarkId - 1) * GOLDEN_CONJUGATE) % 1.0;
        if (hue < 0)
            hue += 1.0;
        Color c = Color.getHSBColor((float) hue, SATURATION, BRIGHTNESS);
        return ColorTools.packRGB(c.getRed(), c.getGreen(), c.getBlue());
    }
}
