package qupath.ext.anchor.transform;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Id-matched correspondences between a source image and a target image.
 * <p>
 * Built by {@link #resolve(Collection, Collection)}, which joins two per-image point sets by
 * landmark id (never by list order), sorts by id ascending for determinism, and records ids present
 * on only one side. This is the sanctioned way to prepare inputs for {@link TransformFitter}; the
 * upstream caller filters each side (e.g. by {@code role}) before calling {@code resolve}, which is
 * how "fit from all points / only manual / only dragged-grid" is expressed.
 */
public final class CorrespondenceSet {

    private final List<Integer> landmarkIds;
    private final List<Point2D> source;
    private final List<Point2D> target;
    private final List<Integer> unmatchedSourceIds;
    private final List<Integer> unmatchedTargetIds;

    private CorrespondenceSet(List<Integer> landmarkIds, List<Point2D> source, List<Point2D> target,
                              List<Integer> unmatchedSourceIds, List<Integer> unmatchedTargetIds) {
        this.landmarkIds = Collections.unmodifiableList(landmarkIds);
        this.source = Collections.unmodifiableList(source);
        this.target = Collections.unmodifiableList(target);
        this.unmatchedSourceIds = Collections.unmodifiableList(unmatchedSourceIds);
        this.unmatchedTargetIds = Collections.unmodifiableList(unmatchedTargetIds);
    }

    /**
     * Join two per-image point sets by landmark id.
     *
     * @throws IllegalArgumentException if either side contains a duplicate landmark id
     */
    public static CorrespondenceSet resolve(Collection<IdPoint> sourcePoints, Collection<IdPoint> targetPoints) {
        Map<Integer, Point2D> sourceById = index(sourcePoints, "source");
        Map<Integer, Point2D> targetById = index(targetPoints, "target");

        TreeSet<Integer> matched = new TreeSet<>(sourceById.keySet());
        matched.retainAll(targetById.keySet());

        List<Integer> ids = new ArrayList<>(matched.size());
        List<Point2D> src = new ArrayList<>(matched.size());
        List<Point2D> tgt = new ArrayList<>(matched.size());
        for (Integer id : matched) {
            ids.add(id);
            src.add(sourceById.get(id));
            tgt.add(targetById.get(id));
        }

        List<Integer> unmatchedSource = new ArrayList<>(sourceById.keySet());
        unmatchedSource.removeAll(matched);
        Collections.sort(unmatchedSource);

        List<Integer> unmatchedTarget = new ArrayList<>(targetById.keySet());
        unmatchedTarget.removeAll(matched);
        Collections.sort(unmatchedTarget);

        return new CorrespondenceSet(ids, src, tgt, unmatchedSource, unmatchedTarget);
    }

    private static Map<Integer, Point2D> index(Collection<IdPoint> points, String side) {
        Map<Integer, Point2D> byId = new LinkedHashMap<>();
        for (IdPoint p : points) {
            if (byId.putIfAbsent(p.landmarkId(), p.point()) != null)
                throw new IllegalArgumentException(
                        "Duplicate landmark id " + p.landmarkId() + " in " + side + " points");
        }
        return byId;
    }

    /** Matched landmark ids, ascending. */
    public List<Integer> landmarkIds() {
        return landmarkIds;
    }

    /** Source points, in the same order as {@link #landmarkIds()}. */
    public List<Point2D> source() {
        return source;
    }

    /** Target points, in the same order as {@link #landmarkIds()}. */
    public List<Point2D> target() {
        return target;
    }

    public List<Integer> unmatchedSourceIds() {
        return unmatchedSourceIds;
    }

    public List<Integer> unmatchedTargetIds() {
        return unmatchedTargetIds;
    }

    /** Number of matched correspondences. */
    public int size() {
        return landmarkIds.size();
    }
}
