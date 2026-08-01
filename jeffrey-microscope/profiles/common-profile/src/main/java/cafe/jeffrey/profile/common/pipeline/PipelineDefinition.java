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
 * The stages one pipeline runs, in order.
 *
 * <p>Stages are identified by opaque string ids and carry no label. That is deliberate: the backend
 * stores and reports stages by id, and the frontend owns the human-facing labels and the grouping of
 * stages into phases. Putting labels here would drag display decisions across the wire and make
 * renaming a heading a backend change.</p>
 *
 * @param pipelineId identifies the pipeline itself (e.g. {@code heap-dump-init}, {@code advisor}); it
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
