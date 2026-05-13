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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * A single stage's outcome inside an {@link InitPipelineResult} snapshot.
 *
 * @param id          stage identifier shared with the frontend's pipeline definition
 *                    (e.g. "load", "parse", "classloaders")
 * @param status      terminal status; one of {@code "completed"} or {@code "skipped"}
 * @param durationMs  elapsed milliseconds the stage took, or {@code null} when skipped
 * @param subPhases   optional fine-grained breakdown of where the stage's time
 *                    went, surfaced to the UI as an expandable accordion.
 *                    {@code null} when the stage has no further instrumentation
 *                    (most stages today); never empty when present.
 */
public record InitStageResult(
        String id,
        String status,
        Long durationMs,
        List<SubPhaseTiming> subPhases
) {

    /**
     * Backwards-compatible constructor for stages without sub-phase data.
     */
    public InitStageResult(String id, String status, Long durationMs) {
        this(id, status, durationMs, null);
    }
}
