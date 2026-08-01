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

import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageResult;
import cafe.jeffrey.profile.common.pipeline.StageStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvisorRunResultTest {

    private static final Instant START = Instant.parse("2026-08-01T06:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T06:00:14Z");

    private static StageResult step(String name, long durationMs) {
        return new StageResult(name, StageStatus.COMPLETED, durationMs, null);
    }

    private static PipelineRunResult run(
            String eventType, PipelineState state, Instant startedAt, List<StageResult> steps) {

        int completed = (int) steps.stream()
                .filter(step -> step.status() == StageStatus.COMPLETED)
                .count();

        return new PipelineRunResult(
                AdvisorStages.PIPELINE_ID, eventType, state, 0L, steps.size(), completed,
                null, null, startedAt, END, steps);
    }

    @Test
    void aggregatesPerTypeAndPerStepTimings() {
        PipelineRunResult cpu = run("jdk.ExecutionSample", PipelineState.COMPLETED, START,
                List.of(step("PREPARING_PROMPT", 400), step("RESOLVING_SOURCE", 200),
                        step("ANALYZING", 8000), step("GROUNDING", 500)));
        PipelineRunResult blocking = run("jdk.JavaMonitorEnter", PipelineState.FAILED, START,
                List.of(step("PREPARING_PROMPT", 100),
                        new StageResult("RESOLVING_SOURCE", StageStatus.FAILED, null, null)));

        AdvisorRunResult result = AdvisorRunResult.from(List.of(cpu, blocking));

        assertEquals(2, result.totalTypes());
        assertEquals(1, result.completedTypes(), "only the CPU type produced findings");
        assertEquals(14000L, result.totalElapsedMs(), "wall clock from first start to finish");

        AdvisorTypeResult cpuResult = result.types().getFirst();
        assertEquals("jdk.ExecutionSample", cpuResult.eventType());
        assertEquals(AdvisorStepProgress.COMPLETED, cpuResult.status());
        assertEquals(9100L, cpuResult.totalMs(), "sum of the four step durations");
        assertEquals(4, cpuResult.steps().size());

        assertEquals(AdvisorStepProgress.FAILED, result.types().get(1).status());
    }

    @Test
    void measuresWallClockAcrossConcurrentTypesRatherThanSummingThem() {
        // The types run at the same time, so a batch that ran two 14-second types took 14 seconds,
        // not 28. Summing would report wall clock that never elapsed.
        PipelineRunResult first = run("jdk.ExecutionSample", PipelineState.COMPLETED, START,
                List.of(step("ANALYZING", 14_000)));
        PipelineRunResult second = run("jdk.JavaMonitorEnter", PipelineState.COMPLETED,
                START.plusSeconds(2), List.of(step("ANALYZING", 12_000)));

        AdvisorRunResult result = AdvisorRunResult.from(List.of(first, second));

        assertEquals(14000L, result.totalElapsedMs());
        assertEquals(END.toEpochMilli(), result.completedAt());
    }
}
