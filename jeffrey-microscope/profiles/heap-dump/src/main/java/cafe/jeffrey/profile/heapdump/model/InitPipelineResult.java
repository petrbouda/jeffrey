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

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of the frontend "Initializing Heap Dump" pipeline result. The
 * backend persists this opaquely (by stage id) so the frontend stays the
 * single source of truth for phase grouping and stage labels.
 *
 * @param totalElapsedMs  wall-clock duration of the whole pipeline run
 * @param totalSteps      number of stages in the pipeline definition
 * @param completedSteps  number of stages that finished with status
 *                        {@code completed} or {@code skipped}
 * @param completedAt     instant the pipeline reported completion
 * @param stages          per-stage outcome, in the order they ran
 */
public record InitPipelineResult(
        long totalElapsedMs,
        int totalSteps,
        int completedSteps,
        Instant completedAt,
        List<InitStageResult> stages
) {
}
