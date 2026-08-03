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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The durable result of a batch run — the timeline the Advisor Overview keeps and re-renders after a
 * reload, mirroring the heap dump's stored init-pipeline result.
 *
 * <p>This is a <em>view</em>, assembled on read from the stored {@code pipeline_runs} rows rather than
 * stored as a document of its own. Each event type persists its own row as it finishes, so a batch is
 * simply every row of the advisor pipeline; aggregating here is what keeps a single generic table able
 * to answer a question phrased in the Advisor's terms.</p>
 *
 * @param totalElapsedMs the wall-clock time from the first type starting to the last one finishing
 * @param completedTypes how many types produced a result
 * @param totalTypes     how many types were analyzed
 * @param completedAt    when the batch finished, as UTC epoch millis
 * @param types          the per-type breakdown
 */
public record AdvisorRunResult(
        long totalElapsedMs,
        int completedTypes,
        int totalTypes,
        Long completedAt,
        List<AdvisorTypeResult> types) {

    public AdvisorRunResult {
        types = types == null ? List.of() : List.copyOf(types);
    }

    /**
     * Assembles the batch view from one stored run per event type.
     *
     * <p>{@code totalElapsedMs} is measured from the earliest start to the latest finish rather than
     * summed across types: they run concurrently, so a sum would report several minutes of wall clock
     * that never elapsed.</p>
     */
    public static AdvisorRunResult from(List<PipelineRunResult> runs) {
        List<AdvisorTypeResult> types = runs.stream()
                .map(AdvisorRunResult::toTypeResult)
                .toList();

        int completedTypes = (int) runs.stream()
                .filter(run -> run.state() == PipelineState.COMPLETED)
                .count();

        Instant startedAt = earliest(runs, PipelineRunResult::startedAt);
        Instant completedAt = latest(runs, PipelineRunResult::completedAt);
        long totalElapsedMs = startedAt != null && completedAt != null
                ? Duration.between(startedAt, completedAt).toMillis()
                : 0L;

        // Epoch millis on the wire: an Instant field would serialize as an ISO string, which the
        // frontend's timestamp rules forbid.
        Long completedAtMillis = completedAt == null ? null : completedAt.toEpochMilli();
        return new AdvisorRunResult(totalElapsedMs, completedTypes, types.size(), completedAtMillis, types);
    }

    private static AdvisorTypeResult toTypeResult(PipelineRunResult run) {
        List<AdvisorStepResult> steps = run.stages().stream()
                .map(stage -> new AdvisorStepResult(
                        stage.id(), stage.status().code(), stage.durationMs()))
                .toList();

        long totalMs = run.stages().stream()
                .map(StageResult::durationMs)
                .filter(duration -> duration != null)
                .mapToLong(Long::longValue)
                .sum();

        return new AdvisorTypeResult(run.scopeId(), statusOf(run), totalMs, steps);
    }

    private static String statusOf(PipelineRunResult run) {
        return run.state() == PipelineState.COMPLETED
                ? AdvisorStepProgress.COMPLETED
                : AdvisorStepProgress.FAILED;
    }

    private static Instant earliest(
            List<PipelineRunResult> runs, Function<PipelineRunResult, Instant> field) {

        return runs.stream().map(field).filter(instant -> instant != null)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private static Instant latest(
            List<PipelineRunResult> runs, Function<PipelineRunResult, Instant> field) {

        return runs.stream().map(field).filter(instant -> instant != null)
                .max(Comparator.naturalOrder()).orElse(null);
    }
}
