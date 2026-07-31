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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrame;
import cafe.jeffrey.shared.common.model.Severity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeverityCalculatorTest {

    private static ProfileFrame frame(String name, double selfPct) {
        return new ProfileFrame(name, selfPct, selfPct, 100, 0, 0, 100);
    }

    private static GroundedClaim grounded(String name, double selfPct) {
        return new GroundedClaim(
                new RecommendationClaim("Title", name, "Some.java"), frame(name, selfPct), true);
    }

    private static GroundedClaim ungrounded(String name) {
        return GroundedClaim.ungrounded(new RecommendationClaim("Title", name, "Some.java"));
    }

    @Nested
    class Thresholds {

        @Test
        void gradesTwentyPercentAndAboveAsCritical() {
            assertEquals(Severity.CRITICAL, SeverityCalculator.fromDominantSharePct(20.0));
            assertEquals(Severity.CRITICAL, SeverityCalculator.fromDominantSharePct(63.2));
        }

        @Test
        void gradesTenToTwentyAsHigh() {
            assertEquals(Severity.HIGH, SeverityCalculator.fromDominantSharePct(10.0));
            assertEquals(Severity.HIGH, SeverityCalculator.fromDominantSharePct(19.9));
        }

        @Test
        void gradesThreeToTenAsMedium() {
            assertEquals(Severity.MEDIUM, SeverityCalculator.fromDominantSharePct(3.0));
            assertEquals(Severity.MEDIUM, SeverityCalculator.fromDominantSharePct(9.9));
        }

        @Test
        void gradesBelowThreeAsLow() {
            assertEquals(Severity.LOW, SeverityCalculator.fromDominantSharePct(2.9));
            assertEquals(Severity.LOW, SeverityCalculator.fromDominantSharePct(0.0));
        }
    }

    @Nested
    class FromClaims {

        @Test
        void gradesFromTheHeaviestGroundedClaim() {
            Severity severity = SeverityCalculator.fromGroundedClaims(List.of(
                    grounded("Small.method", 4.0),
                    grounded("Hot.method", 24.0),
                    grounded("Medium.method", 11.0)));

            assertEquals(Severity.CRITICAL, severity);
        }

        @Test
        void ignoresUngroundedClaimsEntirely() {
            // The ungrounded claim names a frame that was never sampled; letting it raise the grade
            // would let an invented hotspot outrank a measured one.
            Severity severity = SeverityCalculator.fromGroundedClaims(List.of(
                    grounded("Real.method", 4.0),
                    ungrounded("Imagined.method")));

            assertEquals(Severity.MEDIUM, severity);
        }

        @Test
        void gradesLowWhenNothingIsGrounded() {
            Severity severity = SeverityCalculator.fromGroundedClaims(List.of(ungrounded("Imagined.method")));

            assertEquals(Severity.LOW, severity);
        }

        @Test
        void gradesLowWhenThereAreNoClaimsAtAll() {
            assertEquals(Severity.LOW, SeverityCalculator.fromGroundedClaims(List.of()));
        }
    }
}
