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

import cafe.jeffrey.profile.common.pipeline.PipelineRun;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAdvisorRunTest {

    private static final String PROFILE_ID = "p1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T06:00:00Z"), ZoneOffset.UTC);

    private static final List<AdvisorTarget> FOUR = List.of(
            new AdvisorTarget(PROFILE_ID, "jdk.ExecutionSample"),
            new AdvisorTarget(PROFILE_ID, "profiler.WallClockSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.ObjectAllocationSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.JavaMonitorEnter"));

    /**
     * A launched batch with every type's run attached, as {@code AdvisorRunner} leaves it. The pipeline
     * runs are handed back so a test can drive the terminal transitions the registry drives in
     * production.
     */
    private record LaunchedBatch(BatchAdvisorRun batch, List<PipelineRun> runs) {

        static LaunchedBatch of(List<AdvisorTarget> targets) {
            BatchAdvisorRun batch = new BatchAdvisorRun(PROFILE_ID, targets);
            List<PipelineRun> runs = targets.stream()
                    .map(target -> {
                        PipelineRun run = new PipelineRun(
                                AdvisorStages.DEFINITION, target.eventType(), CLOCK);
                        batch.attach(new AdvisorRun(target, run));
                        return run;
                    })
                    .toList();
            return new LaunchedBatch(batch, runs);
        }

        BatchAdvisorProgress progress() {
            return batch.progress();
        }
    }

    private static LaunchedBatch batch() {
        return LaunchedBatch.of(FOUR);
    }

    /** A minimal terminal result for one type, as the registry would deliver it to the batch. */
    private static PipelineRunResult result(AdvisorTarget target) {
        return new PipelineRunResult(
                AdvisorStages.PIPELINE_ID, target.eventType(), PipelineState.COMPLETED,
                0L, 4, 4, null, null,
                Instant.parse("2026-08-01T06:00:00Z"), Instant.parse("2026-08-01T06:00:10Z"), List.of());
    }

    @Nested
    class OverallStatus {

        @Test
        void queuedBeforeAnyTypeStarts() {
            BatchAdvisorProgress progress = batch().progress();

            assertEquals(BatchStatus.QUEUED, progress.status());
            assertEquals(0, progress.done());
            assertEquals(4, progress.total());
            assertEquals(0, progress.pct());
            assertNull(progress.completedAt());
        }

        @Test
        void runningWhenSomeTypesAreInFlight() {
            LaunchedBatch launched = batch();
            launched.runs().getFirst().complete();
            launched.runs().get(1).beginStage(AdvisorStatus.REVIEWING.name());

            BatchAdvisorProgress progress = launched.progress();

            assertEquals(BatchStatus.RUNNING, progress.status());
            assertEquals(1, progress.done());
            assertEquals(25, progress.pct());
            assertNotNull(progress.startedAt());
            assertNull(progress.completedAt(), "not all types have settled");
        }

        @Test
        void completedWhenEveryTypeSettled() {
            LaunchedBatch launched = batch();
            launched.runs().forEach(PipelineRun::complete);

            BatchAdvisorProgress progress = launched.progress();

            assertEquals(BatchStatus.COMPLETED, progress.status());
            assertEquals(4, progress.done());
            assertEquals(100, progress.pct());
            assertNotNull(progress.completedAt());
        }

        @Test
        void aMixOfSuccessAndFailureStillCompletes() {
            LaunchedBatch launched = batch();
            launched.runs().getFirst().complete();
            launched.runs().get(1).fail(null, "boom");
            launched.runs().get(2).complete();
            launched.runs().get(3).complete();

            assertEquals(BatchStatus.COMPLETED, launched.progress().status());
        }

        @Test
        void failedOnlyWhenEveryTypeFailed() {
            LaunchedBatch launched = batch();
            launched.runs().forEach(run -> run.fail(null, "boom"));

            BatchAdvisorProgress progress = launched.progress();

            assertEquals(BatchStatus.FAILED, progress.status());
            assertEquals(4, progress.done());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void isRunningUntilEveryTypeSettles() {
            LaunchedBatch launched = batch();
            launched.runs().getFirst().complete();

            assertTrue(launched.batch().isRunning());

            launched.runs().forEach(PipelineRun::complete);
            assertFalse(launched.batch().isRunning());
        }

        @Test
        void isRunningWhileItsTypesAreStillBeingLaunched() {
            // A batch is published before its runs are scheduled, so the guard against a second batch
            // must treat a half-launched one as in flight rather than as finished.
            BatchAdvisorRun batch = new BatchAdvisorRun(PROFILE_ID, FOUR);

            assertTrue(batch.isRunning());
        }

        @Test
        void cancellingEveryTypeSettlesTheBatch() {
            LaunchedBatch launched = batch();

            // What AdvisorRunner.cancel does through the registry: fail each type's run.
            launched.runs().forEach(run -> run.fail(null, "Cancelled"));

            assertFalse(launched.batch().isRunning());
            launched.progress().types()
                    .forEach(type -> assertEquals(AdvisorStatus.FAILED, type.status()));
        }

        @Test
        void claimsCompletionExactlyOnce() {
            LaunchedBatch launched = batch();
            launched.runs().forEach(PipelineRun::complete);
            FOUR.forEach(target -> launched.batch().record(target, result(target)));

            assertTrue(launched.batch().tryClaimCompletion());
            assertFalse(launched.batch().tryClaimCompletion(),
                    "only the worker that finishes last may store the batch");
        }

        @Test
        void refusesTheClaimUntilEveryResultIsRecorded() {
            // A run turns terminal a moment before its worker records the result. A claim granted on
            // run states alone would let a sibling persist the batch with this type's result missing.
            LaunchedBatch launched = batch();
            launched.runs().forEach(PipelineRun::complete);
            FOUR.stream().limit(3).forEach(target -> launched.batch().record(target, result(target)));

            assertFalse(launched.batch().tryClaimCompletion(),
                    "three of four results recorded must not be enough");

            launched.batch().record(FOUR.getLast(), result(FOUR.getLast()));
            assertTrue(launched.batch().tryClaimCompletion());
        }

        @Test
        void rejectsAnEmptyBatch() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BatchAdvisorRun(PROFILE_ID, List.of()));
        }
    }
}
