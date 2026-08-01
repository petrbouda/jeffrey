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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The stored outcome of one pipeline run: how long it took and what each stage did.
 *
 * <p>This is what survives the process. Live progress is in-memory only, so a run interrupted by a
 * restart is simply gone — which is honest, because the work died with it. What a user needs after the
 * fact is the last completed run, and that is this.</p>
 *
 * @param pipelineId      which pipeline ran
 * @param scopeId         what within the profile it targeted, or {@code ""}
 * @param state           terminal state; {@link PipelineState#COMPLETED} or {@link PipelineState#FAILED}
 * @param totalElapsedMs  wall-clock duration of the whole run
 * @param totalSteps      number of stages in the pipeline definition
 * @param completedSteps  number of stages that finished completed or skipped
 * @param errorCode       machine-readable error code when failed, else {@code null}
 * @param errorMessage    human-readable failure description when failed, else {@code null}
 * @param startedAt       when the run began
 * @param completedAt     when the run reached its terminal state
 * @param stages          per-stage outcome, in the order they ran
 */
public record PipelineRunResult(
        String pipelineId,
        String scopeId,
        PipelineState state,
        long totalElapsedMs,
        int totalSteps,
        int completedSteps,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        List<StageResult> stages
) {

    public PipelineRunResult {
        if (state != null && !state.isTerminal()) {
            throw new IllegalArgumentException("Only a finished run has a result: " + state);
        }
        scopeId = scopeId == null ? "" : scopeId;
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /**
     * Builds the stored result from a run's final progress snapshot. Counting completed steps here
     * rather than at each call site is what keeps the two pipelines' "12 of 13" readings comparable.
     */
    static PipelineRunResult from(
            PipelineProgress progress, Instant startedAt, Instant completedAt, int totalSteps) {

        List<StageResult> stages = progress.stages().stream()
                .map(stage -> new StageResult(
                        stage.id(), stage.status(), stage.durationMs(), stage.subPhases()))
                .toList();

        int completedSteps = (int) stages.stream()
                .filter(stage -> stage.status() == StageStatus.COMPLETED
                        || stage.status() == StageStatus.SKIPPED)
                .count();

        return new PipelineRunResult(
                progress.pipelineId(),
                progress.scopeId(),
                progress.state(),
                Duration.between(startedAt, completedAt).toMillis(),
                totalSteps,
                completedSteps,
                progress.errorCode(),
                progress.errorMessage(),
                startedAt,
                completedAt,
                stages);
    }
}
