/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
