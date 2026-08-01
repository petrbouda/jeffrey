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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The durable result of a batch run — the timeline the Advisor Overview keeps and re-renders after a
 * reload, mirroring the heap-dump {@code InitPipelineResult}. Built from a terminal
 * {@link BatchAdvisorProgress} once every type has settled and stored (serialized) in the profile
 * database.
 *
 * @param totalElapsedMs the wall-clock time from the first type starting to the batch finishing
 * @param completedTypes how many types produced findings
 * @param totalTypes     how many types were analyzed
 * @param completedAt    when the batch finished
 * @param types          the per-type breakdown, in the batch's order
 */
public record AdvisorRunResult(
        long totalElapsedMs,
        int completedTypes,
        int totalTypes,
        Instant completedAt,
        List<AdvisorTypeResult> types) {

    public AdvisorRunResult {
        types = types == null ? List.of() : List.copyOf(types);
    }

    public static AdvisorRunResult from(BatchAdvisorProgress batch, Instant completedAt) {
        List<AdvisorTypeResult> types = batch.types().stream()
                .map(AdvisorRunResult::toTypeResult)
                .toList();

        long completedTypes = types.stream()
                .filter(type -> AdvisorStepProgress.COMPLETED.equals(type.status()))
                .count();

        long totalElapsedMs = batch.startedAt() != null
                ? Duration.between(batch.startedAt(), completedAt).toMillis()
                : 0L;

        return new AdvisorRunResult(totalElapsedMs, (int) completedTypes, types.size(), completedAt, types);
    }

    private static AdvisorTypeResult toTypeResult(AdvisorProgress progress) {
        List<AdvisorStepResult> steps = progress.steps().stream()
                .map(step -> new AdvisorStepResult(step.step(), step.status(), step.durationMs()))
                .toList();

        long totalMs = steps.stream()
                .mapToLong(step -> step.durationMs() != null ? step.durationMs() : 0L)
                .sum();

        return new AdvisorTypeResult(progress.eventType(), typeStatus(progress.status()), totalMs, steps);
    }

    private static String typeStatus(AdvisorStatus status) {
        return status == AdvisorStatus.COMPLETED ? AdvisorStepProgress.COMPLETED : AdvisorStepProgress.FAILED;
    }
}
