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

import java.util.List;

/**
 * Live progress of a pipeline run, polled by the frontend while the run executes in the background.
 *
 * <p>It carries no result payload. A finished run's artifacts live wherever that pipeline stores them,
 * and the page reads them from there — so a reload after completion shows the same thing as a page that
 * watched the run live, instead of two code paths that can disagree.</p>
 *
 * @param pipelineId   which pipeline this is, so one polling component can serve several
 * @param scopeId      what within the profile the run targets (an event type, say), or {@code ""} when
 *                     the pipeline runs once per profile
 * @param state        {@code idle} when no run exists for this key
 * @param errorCode    machine-readable error code when failed, else {@code null}; lets a caller react
 *                     to a specific failure (the heap dump turns one into a repair prompt) without
 *                     parsing a message
 * @param errorMessage human-readable failure description when failed, else {@code null}
 * @param stages       per-stage live statuses in pipeline order; empty when idle
 */
public record PipelineProgress(
        String pipelineId,
        String scopeId,
        PipelineState state,
        String errorCode,
        String errorMessage,
        List<StageProgress> stages
) {

    public PipelineProgress {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public static PipelineProgress idle(String pipelineId) {
        return new PipelineProgress(pipelineId, "", PipelineState.IDLE, null, null, List.of());
    }

    public boolean isRunning() {
        return state == PipelineState.RUNNING;
    }
}
