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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchAdvisorRunTest {

    private static final String PROFILE_ID = "p1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T06:00:00Z"), ZoneOffset.UTC);

    private static final List<AdvisorTarget> FOUR = List.of(
            new AdvisorTarget(PROFILE_ID, "jdk.ExecutionSample"),
            new AdvisorTarget(PROFILE_ID, "profiler.WallClockSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.ObjectAllocationSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.JavaMonitorEnter"));

    private static BatchAdvisorRun batch() {
        return new BatchAdvisorRun(PROFILE_ID, FOUR, CLOCK);
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
            BatchAdvisorRun batch = batch();
            batch.runs().getFirst().completed();
            batch.runs().get(1).analyzing();

            BatchAdvisorProgress progress = batch.progress();

            assertEquals(BatchStatus.RUNNING, progress.status());
            assertEquals(1, progress.done());
            assertEquals(25, progress.pct());
            assertNotNull(progress.startedAt());
            assertNull(progress.completedAt(), "not all types have settled");
        }

        @Test
        void completedWhenEveryTypeSettled() {
            BatchAdvisorRun batch = batch();
            batch.runs().forEach(AdvisorRun::completed);

            BatchAdvisorProgress progress = batch.progress();

            assertEquals(BatchStatus.COMPLETED, progress.status());
            assertEquals(4, progress.done());
            assertEquals(100, progress.pct());
            assertNotNull(progress.completedAt());
        }

        @Test
        void aMixOfSuccessAndFailureStillCompletes() {
            BatchAdvisorRun batch = batch();
            batch.runs().getFirst().completed();
            batch.runs().get(1).failed("boom");
            batch.runs().get(2).completed();
            batch.runs().get(3).completed();

            assertEquals(BatchStatus.COMPLETED, batch.progress().status());
        }

        @Test
        void failedOnlyWhenEveryTypeFailed() {
            BatchAdvisorRun batch = batch();
            batch.runs().forEach(run -> run.failed("boom"));

            BatchAdvisorProgress progress = batch.progress();

            assertEquals(BatchStatus.FAILED, progress.status());
            assertEquals(4, progress.done());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void isRunningUntilEveryTypeSettles() {
            BatchAdvisorRun batch = batch();
            batch.runs().getFirst().completed();

            assertEquals(true, batch.isRunning());

            batch.runs().forEach(AdvisorRun::completed);
            assertFalse(batch.isRunning());
        }

        @Test
        void cancelSettlesEveryType() {
            BatchAdvisorRun batch = batch();

            batch.cancel();

            assertFalse(batch.isRunning());
            batch.progress().types().forEach(type -> assertEquals(AdvisorStatus.FAILED, type.status()));
        }

        @Test
        void rejectsAnEmptyBatch() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BatchAdvisorRun(PROFILE_ID, List.of(), CLOCK));
        }
    }
}
