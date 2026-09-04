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

package cafe.jeffrey.flamegraph.diff;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonScaleTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);

    @Nested
    class Scaling {

        @Test
        void baselineIsProjectedOntoThePrimaryRecordingLength() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, TWO_MINUTES, 5_000L, 10_000L);

            assertEquals(0.5, scale.factor(), 0.0001,
                    "a baseline that ran twice as long is halved onto the primary's time base");
            assertEquals(50L, scale.scaleBaseline(100L));
            assertEquals(5_000L, scale.scaledBaselineTotal(),
                    "the same steady workload, watched twice as long, scales back to the primary total");
        }

        @Test
        void equalDurationsLeaveMeasurementsUntouched() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 100L, 100L);

            assertEquals(1.0, scale.factor(), 0.0001);
            assertEquals(100L, scale.scaleBaseline(100L));
        }

        @Test
        void missingDurationFallsBackToRawCounts() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, Duration.ZERO, 100L, 80L);

            assertFalse(scale.scaled(), "nothing to scale against");
            assertEquals(1.0, scale.factor(), 0.0001);
            assertEquals(80L, scale.scaleBaseline(80L), "raw counts pass through unchanged");
        }

        @Test
        void sharesAreTakenAgainstEachProfilesOwnTotal() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, TWO_MINUTES, 200L, 1_000L);

            assertEquals(25.0, scale.primarySharePct(50L), 0.0001);
            assertEquals(10.0, scale.baselineSharePct(100L), 0.0001,
                    "the baseline's share uses the baseline's own total, unscaled");
        }

        @Test
        void emptyTotalsDoNotDivideByZero() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 0L, 0L);

            assertEquals(0.0, scale.primarySharePct(0L), 0.0001);
            assertEquals(0.0, scale.baselineSharePct(0L), 0.0001);
        }

        @Test
        void negativeTotalsAreRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ComparisonScale(ONE_MINUTE, ONE_MINUTE, -1L, 10L));
        }
    }

    @Nested
    class Warnings {

        @Test
        void comparableRecordingsWarnAboutNothing() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 10_000L, 9_500L);

            assertTrue(scale.warnings().isEmpty(),
                    "same length, comparable volume — nothing to caveat");
        }

        @Test
        void differentRecordingLengthsAreReportedWithTheFactorApplied() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, TWO_MINUTES, 5_000L, 10_000L);

            assertTrue(contains(scale.warnings(), "different length"),
                    "the reader has to know a correction was applied: " + scale.warnings());
        }

        @Test
        void missingDurationsAreReportedAsUncorrectedCounts() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, Duration.ZERO, 5_000L, 10_000L);

            assertTrue(contains(scale.warnings(), "could NOT be scaled"),
                    "an uncorrected comparison must not look like a corrected one");
        }

        @Test
        void volumesThatStayFarApartAfterScalingSuggestDifferentWorkloads() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 100_000L, 1_000L);

            assertTrue(contains(scale.warnings(), "different workloads"),
                    "100x apart at equal length is not a code change: " + scale.warnings());
        }

        @Test
        void thinProfilesAreFlaggedAsNoise() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 12L, 10L);

            assertTrue(contains(scale.warnings(), "sampling noise"));
            assertTrue(contains(scale.warnings(), "very small denominator"));
        }

        @Test
        void anEventTypeOnlyOneProfileRecordedIsAConfigurationFinding() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 10_000L, 0L);

            assertTrue(contains(scale.warnings(), "profiler-configuration difference"),
                    "everything looking new is a settings difference, not a regression");
        }

        @Test
        void anEmptyPrimaryIsReportedRatherThanReadAsEverythingRemoved() {
            ComparisonScale scale = new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 0L, 10_000L);

            assertTrue(contains(scale.warnings(), "primary profile recorded nothing"));
        }
    }

    private static boolean contains(List<String> warnings, String fragment) {
        return warnings.stream().anyMatch(warning -> warning.contains(fragment));
    }
}
