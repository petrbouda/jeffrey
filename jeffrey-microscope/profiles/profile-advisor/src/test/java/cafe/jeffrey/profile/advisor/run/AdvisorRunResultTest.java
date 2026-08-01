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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvisorRunResultTest {

    private static final Instant START = Instant.parse("2026-08-01T06:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T06:00:14Z");

    private static AdvisorStepProgress step(String name, long durationMs) {
        return new AdvisorStepProgress(name, AdvisorStepProgress.COMPLETED, durationMs, null);
    }

    @Test
    void aggregatesPerTypeAndPerStepTimings() {
        AdvisorProgress cpu = new AdvisorProgress(
                "p1", "jdk.ExecutionSample", AdvisorStatus.COMPLETED, "", null, START, END,
                List.of(step("PREPARING_PROMPT", 400), step("RESOLVING_SOURCE", 200),
                        step("ANALYZING", 8000), step("GROUNDING", 500)));
        AdvisorProgress blocking = new AdvisorProgress(
                "p1", "jdk.JavaMonitorEnter", AdvisorStatus.FAILED, "", "boom", START, END,
                List.of(step("PREPARING_PROMPT", 100),
                        new AdvisorStepProgress("RESOLVING_SOURCE", AdvisorStepProgress.FAILED, null, null)));

        BatchAdvisorProgress batch = new BatchAdvisorProgress(
                "p1", BatchStatus.COMPLETED, 2, 2, 100, START, END, List.of(cpu, blocking));

        AdvisorRunResult result = AdvisorRunResult.from(batch, END);

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
}
