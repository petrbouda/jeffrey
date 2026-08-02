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

package cafe.jeffrey.profile.common.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineRunTest {

    private static final PipelineDefinition DEFINITION =
            new PipelineDefinition("test", List.of("first", "second", "third"));

    /** Advances by a fixed step on every read, so a stage's measured duration is deterministic. */
    private static final class TickingClock extends Clock {

        private final Duration step;
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        private TickingClock(Duration step) {
            this.step = step;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            now = now.plus(step);
            return now;
        }
    }

    private static PipelineRun newRun() {
        return new PipelineRun(DEFINITION, "", new TickingClock(Duration.ofSeconds(1)));
    }

    private static Map<String, StageProgress> stagesOf(PipelineRun run) {
        return run.progress().stages().stream()
                .collect(Collectors.toMap(StageProgress::id, Function.identity()));
    }

    @Nested
    @DisplayName("Stage lifecycle")
    class StageLifecycle {

        @Test
        @DisplayName("starts with every declared stage pending, in declaration order")
        void startsPending() {
            PipelineRun run = newRun();

            List<String> ids = run.progress().stages().stream().map(StageProgress::id).toList();

            assertEquals(List.of("first", "second", "third"), ids);
            assertTrue(run.progress().stages().stream()
                    .allMatch(stage -> stage.status() == StageStatus.PENDING));
            assertEquals(PipelineState.RUNNING, run.progress().state());
        }

        @Test
        @DisplayName("records a duration for a stage that ran")
        void recordsDuration() {
            PipelineRun run = newRun();

            run.runStage("first", () -> {
            });

            StageProgress first = stagesOf(run).get("first");
            assertEquals(StageStatus.COMPLETED, first.status());
            assertNotNull(first.durationMs());
        }

        @Test
        @DisplayName("marks only the failing stage failed, and rethrows so the run ends")
        void failsOnlyTheFailingStage() {
            PipelineRun run = newRun();
            run.runStage("first", () -> {
            });

            assertThrows(IllegalStateException.class, () -> run.runStage("second", () -> {
                throw new IllegalStateException("boom");
            }));

            Map<String, StageProgress> stages = stagesOf(run);
            assertEquals(StageStatus.COMPLETED, stages.get("first").status());
            assertEquals(StageStatus.FAILED, stages.get("second").status());
            assertEquals(StageStatus.PENDING, stages.get("third").status());
        }

        @Test
        @DisplayName("keeps skipped distinct from completed")
        void keepsSkippedDistinct() {
            PipelineRun run = newRun();

            run.skipStage("first");

            assertEquals(StageStatus.SKIPPED, stagesOf(run).get("first").status());
        }

        @Test
        @DisplayName("rejects a stage the pipeline never declared, rather than inventing one")
        void rejectsUnknownStage() {
            PipelineRun run = newRun();

            assertThrows(IllegalArgumentException.class, () -> run.beginStage("nope"));
        }
    }

    @Nested
    @DisplayName("Live timer")
    class LiveTimer {

        @Test
        @DisplayName("reports elapsed time for the stage it points at")
        void reportsElapsedForActiveStage() {
            PipelineRun run = newRun();

            run.beginStage("first");

            StageProgress first = stagesOf(run).get("first");
            assertEquals(StageStatus.IN_PROGRESS, first.status());
            assertNotNull(first.elapsedMs());
        }

        @Test
        @DisplayName("points at exactly one stage, so two in-progress stages never share a clock")
        void pointsAtExactlyOneStage() {
            PipelineRun run = newRun();

            // The heap dump's index group does exactly this: begin one stage, then begin the next
            // before the first has been completed.
            run.beginStage("first");
            run.beginStage("second");

            Map<String, StageProgress> stages = stagesOf(run);
            assertEquals(StageStatus.IN_PROGRESS, stages.get("first").status());
            assertEquals(StageStatus.IN_PROGRESS, stages.get("second").status());
            assertNull(stages.get("first").elapsedMs());
            assertNotNull(stages.get("second").elapsedMs());
        }

        @Test
        @DisplayName("stops once the active stage is cleared")
        void stopsWhenCleared() {
            PipelineRun run = newRun();
            run.beginStage("first");

            run.clearActiveStage();

            assertNull(stagesOf(run).get("first").elapsedMs());
        }
    }

    @Nested
    @DisplayName("Result")
    class Result {

        @Test
        @DisplayName("counts completed and skipped stages against the declared total")
        void countsCompletedSteps() {
            PipelineRun run = newRun();
            run.runStage("first", () -> {
            });
            run.skipStage("second");
            run.complete();

            PipelineRunResult result = run.result(Instant.parse("2026-01-01T00:05:00Z"));

            assertEquals(3, result.totalSteps());
            assertEquals(2, result.completedSteps());
            assertEquals(PipelineState.COMPLETED, result.state());
            assertEquals(3, result.stages().size());
        }

        @Test
        @DisplayName("carries the failure code so a caller can react to a specific failure")
        void carriesFailureCode() {
            PipelineRun run = newRun();
            run.fail("HEAP_DUMP_NEEDS_SANITIZATION", "needs repair");

            PipelineRunResult result = run.result(Instant.parse("2026-01-01T00:05:00Z"));

            assertEquals(PipelineState.FAILED, result.state());
            assertEquals("HEAP_DUMP_NEEDS_SANITIZATION", result.errorCode());
            assertEquals("needs repair", result.errorMessage());
        }

        @Test
        @DisplayName("stays failed when the cancelled work later reports completion")
        void firstTerminalTransitionWins() {
            PipelineRun run = newRun();
            run.beginStage("first");

            assertTrue(run.fail("CANCELLED", "Cancelled"));
            // The zombie worker returns minutes later and reports success — too late.
            assertFalse(run.complete());

            PipelineRunResult result = run.result(Instant.parse("2026-01-01T00:05:00Z"));
            assertEquals(PipelineState.FAILED, result.state());
            assertEquals("Cancelled", result.errorMessage());
        }

        /**
         * A cancelled run's worker is interrupted, but whether the work notices is up to the work — an
         * HTTP call already in flight keeps going and announces its next phase on the way out. Opening
         * that phase would restart the live timer on a run that already ended, leaving the timeline
         * with a stage spinning forever underneath a "Run failed" heading.
         */
        @Test
        @DisplayName("ignores phase announcements from a worker that outlived the cancellation")
        void refusesToOpenStagesAfterTheRunEnded() {
            PipelineRun run = newRun();
            run.beginStage("first");

            assertTrue(run.fail(null, "Cancelled"));
            run.advanceTo("second");
            run.beginStage("third");

            Map<String, StageProgress> stages = stagesOf(run);
            assertEquals(StageStatus.FAILED, stages.get("first").status(), "the stage that was cut off");
            assertEquals(StageStatus.PENDING, stages.get("second").status());
            assertEquals(StageStatus.PENDING, stages.get("third").status());
            assertNull(stages.get("second").elapsedMs(), "no live timer on an ended run");
            assertNull(stages.get("third").elapsedMs(), "no live timer on an ended run");
        }

        @Test
        @DisplayName("stays completed when a late cancellation arrives")
        void lateFailureCannotOverturnCompletion() {
            PipelineRun run = newRun();
            run.runStage("first", () -> {
            });

            assertTrue(run.complete());
            assertFalse(run.fail(null, "Cancelled"));

            assertEquals(PipelineState.COMPLETED,
                    run.result(Instant.parse("2026-01-01T00:05:00Z")).state());
        }

        @Test
        @DisplayName("refuses to describe a run that has not finished")
        void refusesUnfinishedRun() {
            assertThrows(IllegalArgumentException.class, () -> new PipelineRunResult(
                    "test", "", PipelineState.RUNNING, 0, 3, 0, null, null,
                    Instant.EPOCH, Instant.EPOCH, List.of()));
        }
    }
}
