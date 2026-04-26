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

package cafe.jeffrey.profile.guardian.prereq;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.guard.GuardAnalysisResult;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.shared.common.model.EventSubtype;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerequisitesEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-04-01T10:00:00Z");

    private static ProfileInfo profile(Instant started, Instant finished) {
        return new ProfileInfo(
                "test", "proj", "ws", "name",
                RecordingEventSource.ASYNC_PROFILER,
                started, finished, T0, true, false, "rec");
    }

    private static EventSummary summary(Type type) {
        return new EventSummary(type.code(), type.code(), RecordingEventSource.JDK,
                EventSubtype.EXECUTION_SAMPLE, 1, 1, false, false,
                List.of(), Map.of(), Map.of());
    }

    private static GuardAnalysisResult findByRule(List<GuardianResult> results, String rule) {
        return results.stream()
                .map(GuardianResult::analysisItem)
                .filter(r -> rule.equals(r.rule()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result with rule: " + rule));
    }

    // All results must carry Guard.Category.PREREQUISITES so the "Prerequisites" panel in the
    // frontend picks them up via the string literal that Category.PREREQUISITES.getLabel() emits.
    @Test
    void everyResultIsInPrerequisitesCategory() {
        List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                profile(T0, T0.plusSeconds(120)),
                Preconditions.builder()
                        .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                        .withDebugSymbolsAvailable(true)
                        .withKernelSymbolsAvailable(true)
                        .withEventTypes(List.of(summary(Type.EXECUTION_SAMPLE), summary(Type.JAVA_MONITOR_ENTER)))
                        .build());

        assertEquals(4, results.size(), "Four prereq checks: event source, duration, coverage, debug symbols");
        for (GuardianResult r : results) {
            assertEquals(Guard.Category.PREREQUISITES, r.analysisItem().category());
        }
    }

    @Nested
    class EventSourceCheck {

        @Test
        void asyncProfiler_isOk() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER).build());
            assertEquals(Severity.OK, findByRule(results, "Event Source").severity());
        }

        @Test
        void jdkJfr_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.JDK).build());
            assertEquals(Severity.INFO, findByRule(results, "Event Source").severity());
        }

        @Test
        void unknown_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().build()); // null source
            assertEquals(Severity.INFO, findByRule(results, "Event Source").severity());
        }
    }

    @Nested
    class RecordingDurationCheck {

        @Test
        void longRecording_isOk() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(300)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER).build());
            assertEquals(Severity.OK, findByRule(results, "Recording Duration").severity());
        }

        @Test
        void shortRecording_isWarning() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(30)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER).build());
            assertEquals(Severity.WARNING, findByRule(results, "Recording Duration").severity());
        }

        @Test
        void missingTimestamps_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(null, null),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER).build());
            assertEquals(Severity.INFO, findByRule(results, "Recording Duration").severity());
        }
    }

    @Nested
    class EventCoverageCheck {

        @Test
        void zeroGroups_isWarning() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withEventTypes(List.of()).build());
            assertEquals(Severity.WARNING, findByRule(results, "Event Coverage").severity());
        }

        @Test
        void singleGroup_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withEventTypes(List.of(summary(Type.EXECUTION_SAMPLE))).build());
            assertEquals(Severity.INFO, findByRule(results, "Event Coverage").severity());
        }

        @Test
        void twoGroups_isOk() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withEventTypes(List.of(
                                    summary(Type.EXECUTION_SAMPLE),
                                    summary(Type.JAVA_MONITOR_ENTER))).build());
            assertEquals(Severity.OK, findByRule(results, "Event Coverage").severity());
        }

        @Test
        void allocationEventsCountAsOneGroup() {
            // All three allocation event types map to one Allocation group; expect INFO (single group).
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withEventTypes(List.of(
                                    summary(Type.OBJECT_ALLOCATION_SAMPLE),
                                    summary(Type.OBJECT_ALLOCATION_IN_NEW_TLAB),
                                    summary(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB))).build());
            assertEquals(Severity.INFO, findByRule(results, "Event Coverage").severity());
        }
    }

    @Nested
    class DebugSymbolsCheck {

        @Test
        void nonAsyncProfilerSource_reportsOkWithNotApplicableScore() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder().withEventSource(RecordingEventSource.JDK).build());
            GuardAnalysisResult debug = findByRule(results, "Debug Symbols");
            assertEquals(Severity.OK, debug.severity());
            assertEquals("n/a", debug.score());
        }

        @Test
        void bothSymbolsAvailable_isOk() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder()
                            .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withDebugSymbolsAvailable(true)
                            .withKernelSymbolsAvailable(true).build());
            assertEquals(Severity.OK, findByRule(results, "Debug Symbols").severity());
        }

        @Test
        void missingDebugSymbols_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder()
                            .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withDebugSymbolsAvailable(false)
                            .withKernelSymbolsAvailable(true).build());
            GuardAnalysisResult debug = findByRule(results, "Debug Symbols");
            assertEquals(Severity.INFO, debug.severity());
            assertTrue(debug.score().contains("debug=no"));
        }

        @Test
        void missingKernelSymbols_isInfo() {
            List<GuardianResult> results = PrerequisitesEvaluator.evaluate(
                    profile(T0, T0.plusSeconds(120)),
                    Preconditions.builder()
                            .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                            .withDebugSymbolsAvailable(true)
                            .withKernelSymbolsAvailable(false).build());
            GuardAnalysisResult debug = findByRule(results, "Debug Symbols");
            assertEquals(Severity.INFO, debug.severity());
            assertTrue(debug.score().contains("kernel=no"));
        }
    }
}
