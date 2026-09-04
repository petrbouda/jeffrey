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

import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonMarkdownBuilderTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);
    private static final int LIMIT = 10;

    @Nested
    class Header {

        @Test
        void statesWhatWasComparedAndHowItWasCorrected() {
            String markdown = render(ONE_MINUTE, TWO_MINUTES);

            assertTrue(markdown.contains("event_type: jdk.ExecutionSample"));
            assertTrue(markdown.contains("primary_duration: PT1M"));
            assertTrue(markdown.contains("baseline_duration: PT2M"));
            assertTrue(markdown.contains("scaled: yes"));
            assertTrue(markdown.contains("baseline_scale_factor: 0.500"),
                    "the correction is shown, not merely applied");
        }

        @Test
        void saysSoWhenNoTimeBaseCorrectionWasPossible() {
            String markdown = render(ONE_MINUTE, Duration.ZERO);

            assertTrue(markdown.contains("scaled: no"));
            assertTrue(markdown.contains("could NOT be scaled"),
                    "an uncorrected comparison must announce itself");
        }
    }

    @Nested
    class Comparability {

        @Test
        void warningsLeadTheDocumentWhenTheRecordingsDoNotMatch() {
            String markdown = render(ONE_MINUTE, TWO_MINUTES);

            int comparability = markdown.indexOf("## Comparability");
            int regressed = markdown.indexOf("## Regressed");
            assertTrue(comparability > 0 && comparability < regressed,
                    "the caveats have to be read before the numbers they qualify");
            assertTrue(markdown.contains("different length"));
        }

        @Test
        void aCleanPairIsSaidToBeClean() {
            String markdown = render(ONE_MINUTE, ONE_MINUTE);

            assertTrue(markdown.contains("No comparability problems detected"));
        }
    }

    @Nested
    class Movements {

        @Test
        void aRegressionIsReportedWithItsDirectionAndItsShareMovement() {
            String markdown = render(ONE_MINUTE, ONE_MINUTE);

            assertTrue(markdown.contains("| handler |"), "the moved method gets a row");
            assertTrue(markdown.contains("| +4000 |"), "deltas carry their sign");
            assertTrue(markdown.contains("pp |"), "share movement is in percentage points");
        }

        @Test
        void workWithNoBaselineIsReportedAsNewRatherThanAsAPercentage() {
            Frame primary = node("all", 100L);
            primary.put("brandNew", leaf("brandNew", 40L));
            Frame baseline = node("all", 100L);

            String markdown = render(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            assertTrue(markdown.contains("| new |"),
                    "appearing from nothing is a different finding from a large ratio");
            assertFalse(markdown.contains("Infinity"));
        }

        @Test
        void anEmptyPairSaysNothingWasComparedRatherThanNothingChanged() {
            String markdown = render(node("all", 0L), node("all", 0L), ONE_MINUTE, ONE_MINUTE);

            assertTrue(markdown.contains("nothing to compare"));
        }
    }

    @Nested
    class Renames {

        @Test
        void aRenameSuspicionIsPresentedAsUnconfirmed() {
            Frame primary = node("all", 100L);
            primary.put("newName", leaf("newName", 30L));
            Frame baseline = node("all", 100L);
            baseline.put("oldName", leaf("oldName", 30L));

            String markdown = render(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            assertTrue(markdown.contains("## Candidate renames (unconfirmed)"));
            assertTrue(markdown.contains("| newName |"));
            assertTrue(markdown.contains("oldName"));
        }

        @Test
        void nothingIsClaimedWhenNoPairLinesUp() {
            String markdown = render(ONE_MINUTE, ONE_MINUTE);

            // The preamble explains what a rename candidate is either way; what must be absent is the
            // table, which would present a guess as a finding.
            assertFalse(markdown.contains("| appeared | at |"));
        }
    }

    private static String render(Duration primaryDuration, Duration baselineDuration) {
        // Volumes deliberately above the thin-profile floor: a small profile earns a comparability
        // warning of its own, which would drown out whatever a given test is actually asserting.
        Frame primary = node("all", 10_000L);
        primary.put("handler", node("handler", 6_000L));
        Frame baseline = node("all", 10_000L);
        baseline.put("handler", node("handler", 2_000L));
        return render(primary, baseline, primaryDuration, baselineDuration);
    }

    private static String render(
            Frame primary, Frame baseline, Duration primaryDuration, Duration baselineDuration) {

        return ProfileComparison.rankedMarkdown(
                Type.EXECUTION_SAMPLE,
                new DiffTreeGenerator(primary, baseline).generate(),
                primaryDuration,
                baselineDuration,
                LIMIT);
    }

    private static Frame node(String methodName, long samples) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, samples, samples, false);
        return frame;
    }

    private static Frame leaf(String methodName, long samples) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, samples, samples, true);
        return frame;
    }
}
