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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageResult;
import cafe.jeffrey.profile.common.pipeline.StageStatus;
import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both pipelines store their runs in one table, so the round-trip that matters is the one that proves
 * they coexist: a heap-dump run keyed by an empty scope alongside per-event-type advisor runs.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcPipelineRunRepositoryTest {

    private static final String HEAP_DUMP = "heap-dump-init";
    private static final String ADVISOR = "advisor";

    private static final Instant STARTED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-01-01T10:02:30Z");

    private static JdbcPipelineRunRepository repository(DataSource dataSource) {
        return new JdbcPipelineRunRepository(new DatabaseClientProvider(dataSource));
    }

    private static PipelineRunResult completed(String pipelineId, String scopeId, List<StageResult> stages) {
        return new PipelineRunResult(
                pipelineId, scopeId, PipelineState.COMPLETED, 150_000L, stages.size(), stages.size(),
                null, null, STARTED_AT, COMPLETED_AT, stages);
    }

    private static StageResult stage(String id, long durationMs) {
        return new StageResult(id, StageStatus.COMPLETED, durationMs, null);
    }

    @Nested
    class RoundTrip {

        @Test
        void restoresEveryFieldOfACompletedRun(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);
            PipelineRunResult stored = completed(HEAP_DUMP, "", List.of(stage("load", 130L)));

            repository.upsert(stored);

            PipelineRunResult found = repository.find(HEAP_DUMP, "").orElseThrow();
            assertEquals(stored.pipelineId(), found.pipelineId());
            assertEquals("", found.scopeId());
            assertEquals(PipelineState.COMPLETED, found.state());
            assertEquals(150_000L, found.totalElapsedMs());
            assertEquals(1, found.totalSteps());
            assertEquals(1, found.completedSteps());
            assertNull(found.errorCode());
            assertEquals(STARTED_AT, found.startedAt());
            assertEquals(COMPLETED_AT, found.completedAt());
        }

        @Test
        void keepsStageTimingsAndTheirSubPhases(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);
            StageResult load = new StageResult("load", StageStatus.COMPLETED, 130L, List.of(
                    new SubPhaseTiming("walk_top_level", 100L, null),
                    new SubPhaseTiming("drop_indexes", 30L, null)));
            StageResult strings = new StageResult("strings", StageStatus.SKIPPED, null, null);

            repository.upsert(completed(HEAP_DUMP, "", List.of(load, strings)));

            List<StageResult> stages = repository.find(HEAP_DUMP, "").orElseThrow().stages();
            assertEquals(2, stages.size());
            assertEquals(130L, stages.getFirst().durationMs());
            assertEquals(List.of("walk_top_level", "drop_indexes"),
                    stages.getFirst().subPhases().stream().map(SubPhaseTiming::name).toList());
            assertEquals(StageStatus.SKIPPED, stages.get(1).status());
            assertNull(stages.get(1).durationMs());
        }

        @Test
        void keepsTheFailureCodeThatDrivesTheRepairPrompt(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);
            PipelineRunResult failed = new PipelineRunResult(
                    HEAP_DUMP, "", PipelineState.FAILED, 4_000L, 13, 2,
                    "HEAP_DUMP_NEEDS_SANITIZATION", "The dump needs repair",
                    STARTED_AT, COMPLETED_AT, List.of(stage("load", 130L)));

            repository.upsert(failed);

            PipelineRunResult found = repository.find(HEAP_DUMP, "").orElseThrow();
            assertEquals(PipelineState.FAILED, found.state());
            assertEquals("HEAP_DUMP_NEEDS_SANITIZATION", found.errorCode());
            assertEquals("The dump needs repair", found.errorMessage());
        }

        @Test
        void returnsEmptyWhenNothingHasRun(DataSource dataSource) {
            Optional<PipelineRunResult> found = repository(dataSource).find(ADVISOR, "jdk.ExecutionSample");

            assertTrue(found.isEmpty());
        }
    }

    @Nested
    class Coexistence {

        @Test
        void keepsOneRunPerPipelineAndScope(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);

            repository.upsert(completed(HEAP_DUMP, "", List.of(stage("load", 130L))));
            repository.upsert(completed(ADVISOR, "jdk.ExecutionSample", List.of(stage("prompt", 10L))));
            repository.upsert(completed(ADVISOR, "jdk.ObjectAllocationSample", List.of(stage("prompt", 20L))));

            assertEquals(1, repository.findAll(HEAP_DUMP).size());
            List<PipelineRunResult> advisorRuns = repository.findAll(ADVISOR);
            assertEquals(2, advisorRuns.size());
            assertEquals(List.of("jdk.ExecutionSample", "jdk.ObjectAllocationSample"),
                    advisorRuns.stream().map(PipelineRunResult::scopeId).toList());
        }

        @Test
        void replacesTheRunForAScopeRatherThanAppendingToIt(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);

            repository.upsert(completed(ADVISOR, "jdk.ExecutionSample", List.of(stage("prompt", 10L))));
            repository.upsert(completed(ADVISOR, "jdk.ExecutionSample",
                    List.of(stage("prompt", 10L), stage("source", 5L))));

            List<PipelineRunResult> runs = repository.findAll(ADVISOR);
            assertEquals(1, runs.size());
            assertEquals(2, runs.getFirst().stages().size());
        }

        @Test
        void deletingOnePipelineLeavesTheOtherAlone(DataSource dataSource) {
            JdbcPipelineRunRepository repository = repository(dataSource);
            repository.upsert(completed(HEAP_DUMP, "", List.of(stage("load", 130L))));
            repository.upsert(completed(ADVISOR, "jdk.ExecutionSample", List.of(stage("prompt", 10L))));

            repository.deleteAll(ADVISOR);

            assertTrue(repository.findAll(ADVISOR).isEmpty());
            assertEquals(1, repository.findAll(HEAP_DUMP).size());
        }
    }
}
