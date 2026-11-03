package qupath.ext.anchor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LandmarksTest {

    @Test
    void lowestFreeIdIsOneWhenEmpty() {
        assertEquals(1, Landmarks.lowestFreeId(Set.of()));
    }

    @Test
    void lowestFreeIdContinuesAfterContiguousIds() {
        assertEquals(4, Landmarks.lowestFreeId(Set.of(1, 2, 3)));
    }

    @Test
    void lowestFreeIdFillsGap() {
        // Issue #1: LM-08 removed from 1..18 -> the next added landmark is 8, not 19.
        Set<Integer> used = new HashSet<>();
        for (int i = 1; i <= 18; i++)
            used.add(i);
        used.remove(8);
        assertEquals(8, Landmarks.lowestFreeId(used));
    }

    @Test
    void lowestFreeIdFillsLowestOfSeveralGaps() {
        assertEquals(1, Landmarks.lowestFreeId(Set.of(2, 3, 5)));
        assertEquals(4, Landmarks.lowestFreeId(Set.of(1, 2, 3, 5, 7)));
    }
}
