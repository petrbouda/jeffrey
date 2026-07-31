/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.performance.analyst.regression;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrame;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrameIndex;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileComparatorTest {

    private static final String EVENT_TYPE = "jdk.ExecutionSample";
    private static final double THRESHOLD_PP = 2.0;

    private static ProfileFrameIndex index(Object... namesAndShares) {
        List<ProfileFrame> frames = new ArrayList<>();
        for (int i = 0; i < namesAndShares.length; i += 2) {
            String name = (String) namesAndShares[i];
            double selfPct = ((Number) namesAndShares[i + 1]).doubleValue();
            frames.add(new ProfileFrame(name, selfPct, selfPct, 100, 0, 0, 100));
        }
        return new ProfileFrameIndex(frames);
    }

    @Nested
    class Detection {

        @Test
        void reportsAFrameThatGrewBeyondTheThreshold() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("RateTable.lookup", 9.1),
                    index("RateTable.lookup", 23.4),
                    THRESHOLD_PP);

            assertEquals(1, comparison.regressed().size());
            FrameDelta delta = comparison.regressed().getFirst();
            assertEquals("RateTable.lookup", delta.name());
            assertEquals(14.3, delta.deltaPp(), 0.001);
            assertTrue(comparison.hasRegressions());
        }

        @Test
        void reportsAFrameThatShrankAsAnImprovement() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("CacheLoader.refresh", 11.7),
                    index("CacheLoader.refresh", 4.9),
                    THRESHOLD_PP);

            assertTrue(comparison.regressed().isEmpty());
            assertEquals(1, comparison.improved().size());
            assertEquals(-6.8, comparison.improved().getFirst().deltaPp(), 0.001);
        }

        @Test
        void ignoresMovementBelowTheThreshold() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("JsonMapper.writeValue", 16.3),
                    index("JsonMapper.writeValue", 16.1),
                    THRESHOLD_PP);

            assertTrue(comparison.regressed().isEmpty());
            assertTrue(comparison.improved().isEmpty());
        }

        @Test
        void treatsAFrameAbsentFromTheBaselineAsNewCost() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("Existing.method", 10.0),
                    index("Existing.method", 10.0, "Brand.newMethod", 7.5),
                    THRESHOLD_PP);

            assertEquals(1, comparison.regressed().size());
            assertTrue(comparison.regressed().getFirst().isNew());
        }

        @Test
        void treatsAFrameMissingFromTheCurrentProfileAsFullyImproved() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("Gone.method", 8.0),
                    index("Other.method", 3.0),
                    THRESHOLD_PP);

            assertTrue(comparison.improved().stream().anyMatch(delta -> delta.name().equals("Gone.method")));
        }
    }

    @Nested
    class Ordering {

        @Test
        void listsTheWorstRegressionFirst() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("Small.grow", 1.0, "Big.grow", 1.0),
                    index("Small.grow", 4.0, "Big.grow", 20.0),
                    THRESHOLD_PP);

            assertEquals("Big.grow", comparison.regressed().getFirst().name());
        }

        @Test
        void listsTheBiggestImprovementFirst() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("Small.shrink", 5.0, "Big.shrink", 30.0),
                    index("Small.shrink", 2.0, "Big.shrink", 4.0),
                    THRESHOLD_PP);

            assertEquals("Big.shrink", comparison.improved().getFirst().name());
        }
    }

    @Nested
    class PromptFragment {

        @Test
        void rendersEachRegressionWithItsDelta() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE,
                    index("RateTable.lookup", 9.1),
                    index("RateTable.lookup", 23.4),
                    THRESHOLD_PP);

            String fragment = ProfileComparator.toPromptFragment(comparison);

            assertTrue(fragment.contains("RateTable.lookup"));
            assertTrue(fragment.contains("9.1%"));
            assertTrue(fragment.contains("23.4%"));
            assertTrue(fragment.contains("+14.3 pp"));
        }

        @Test
        void saysSoWhenNothingMoved() {
            ProfileComparison comparison = ProfileComparator.compare(
                    EVENT_TYPE, index("Stable.method", 10.0), index("Stable.method", 10.0), THRESHOLD_PP);

            String fragment = ProfileComparator.toPromptFragment(comparison);

            assertTrue(fragment.contains("No frame moved"));
            assertFalse(comparison.hasRegressions());
        }
    }
}
