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
 * The stages one pipeline runs, in order.
 *
 * <p>Stages are identified by opaque string ids and carry no label. That is deliberate: the backend
 * stores and reports stages by id, and the frontend owns the human-facing labels and the grouping of
 * stages into phases. Putting labels here would drag display decisions across the wire and make
 * renaming a heading a backend change.</p>
 *
 * @param pipelineId identifies the pipeline itself (e.g. {@code heap-dump-init}, {@code profile-init}); it
 *                   is the key a stored run is filed under
 * @param stageIds   the stage ids in run order; must be non-empty and unique
 */
public record PipelineDefinition(String pipelineId, List<String> stageIds) {

    public PipelineDefinition {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("Pipeline id must not be blank");
        }
        if (stageIds == null || stageIds.isEmpty()) {
            throw new IllegalArgumentException("A pipeline must declare at least one stage");
        }
        stageIds = List.copyOf(stageIds);
        if (stageIds.size() != stageIds.stream().distinct().count()) {
            throw new IllegalArgumentException("Duplicate stage ids in pipeline: " + pipelineId);
        }
    }

    public int stageCount() {
        return stageIds.size();
    }
}
