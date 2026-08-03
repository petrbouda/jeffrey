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

package cafe.jeffrey.profile.advisor.run;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeverityCalculatorTest {

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
    class SelfRatherThanTotal {

        @Test
        void doesNotGradeAnOrchestrationFrameAsCritical() {
            // A root-adjacent frame's *total* share is ~100% by construction. Grading from that would
            // make every profile CRITICAL, which is why the input is a self share — here, a run whose
            // heaviest method only spends 1.2% of samples in itself.
            assertEquals(Severity.LOW, SeverityCalculator.fromDominantSharePct(1.2));
        }

        @Test
        void gradesAProfileWithNoSamplesAsLow() {
            assertEquals(Severity.LOW, SeverityCalculator.fromDominantSharePct(0.0));
        }
    }
}
