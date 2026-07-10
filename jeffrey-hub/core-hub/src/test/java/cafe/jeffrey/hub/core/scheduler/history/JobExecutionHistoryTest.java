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

package cafe.jeffrey.hub.core.scheduler.history;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobExecutionHistoryTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private final JobExecutionHistory history = new JobExecutionHistory();

    private static JobExecution execution(JobType jobType, Instant startedAt) {
        return new JobExecution(
                jobType, startedAt, Duration.ofMillis(10), JobExecutionStatus.SUCCESS, null, List.of(), null);
    }

    @Nested
    class Bounding {

        @Test
        void keepsOnlyTheNewestExecutionsPerJobType() {
            int total = JobExecutionHistory.MAX_EXECUTIONS_PER_JOB_TYPE + 25;
            for (int i = 0; i < total; i++) {
                history.add(execution(JobType.TEMP_DIRECTORY_CLEANER, BASE_INSTANT.plusSeconds(i)));
            }

            List<JobExecution> all = history.all();
            assertEquals(JobExecutionHistory.MAX_EXECUTIONS_PER_JOB_TYPE, all.size());

            // The oldest 25 were evicted: the last kept one is number 25 (0-based)
            assertEquals(BASE_INSTANT.plusSeconds(total - 1), all.getFirst().startedAt());
            assertEquals(BASE_INSTANT.plusSeconds(25), all.getLast().startedAt());
        }

        @Test
        void capIsPerJobType_notGlobal() {
            int perType = JobExecutionHistory.MAX_EXECUTIONS_PER_JOB_TYPE;
            for (int i = 0; i < perType + 10; i++) {
                history.add(execution(JobType.TEMP_DIRECTORY_CLEANER, BASE_INSTANT.plusSeconds(i)));
                history.add(execution(JobType.DELETED_PROJECTS_CLEANER, BASE_INSTANT.plusSeconds(i)));
            }

            assertEquals(2 * perType, history.all().size());
        }
    }

    @Nested
    class Ordering {

        @Test
        void allReturnsNewestFirst_acrossJobTypes() {
            history.add(execution(JobType.TEMP_DIRECTORY_CLEANER, BASE_INSTANT.plusSeconds(1)));
            history.add(execution(JobType.DELETED_PROJECTS_CLEANER, BASE_INSTANT.plusSeconds(3)));
            history.add(execution(JobType.PROJECTS_SYNCHRONIZER, BASE_INSTANT.plusSeconds(2)));

            List<Instant> startedAts = history.all().stream().map(JobExecution::startedAt).toList();
            assertEquals(
                    List.of(BASE_INSTANT.plusSeconds(3), BASE_INSTANT.plusSeconds(2), BASE_INSTANT.plusSeconds(1)),
                    startedAts);
        }

        @Test
        void emptyHistory_returnsEmptyList() {
            assertTrue(history.all().isEmpty());
        }
    }
}
