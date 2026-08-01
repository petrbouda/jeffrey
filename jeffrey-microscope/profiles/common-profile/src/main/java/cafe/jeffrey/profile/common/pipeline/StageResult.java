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
 * A single stage's outcome inside a stored {@link PipelineRunResult}.
 *
 * @param id         stage identifier shared with the frontend's pipeline definition
 * @param status     terminal status the stage reached
 * @param durationMs elapsed milliseconds the stage took, or {@code null} when it never ran
 * @param subPhases  optional fine-grained breakdown of where the stage's time went, surfaced to the UI
 *                   as an expandable accordion; {@code null} when the stage has no further
 *                   instrumentation (most stages), never empty when present
 */
public record StageResult(
        String id,
        StageStatus status,
        Long durationMs,
        List<SubPhaseTiming> subPhases
) {

    public StageResult(String id, StageStatus status, Long durationMs) {
        this(id, status, durationMs, null);
    }
}
