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
 * One stage of a running (or finished) server-side init pipeline.
 *
 * @param id         stage identifier shared with the frontend's pipeline
 *                   definition (e.g. "index", "dominator", "consumers")
 * @param status     {@code pending}, {@code in_progress}, {@code completed} or {@code failed}
 * @param durationMs elapsed milliseconds once terminal, else {@code null}
 * @param elapsedMs  milliseconds spent so far while {@code in_progress}, measured
 *                   with the backend clock so a reconnecting frontend can resume
 *                   the stage timer without client/server clock skew; else {@code null}
 * @param subPhases  fine-grained timing breakdown when available, else {@code null}
 */
public record HeapDumpInitStageProgress(
        String id,
        String status,
        Long durationMs,
        Long elapsedMs,
        List<SubPhaseTiming> subPhases
) {

    public static final String STATUS_PENDING = "pending";

    public static final String STATUS_IN_PROGRESS = "in_progress";

    public static final String STATUS_COMPLETED = "completed";

    public static final String STATUS_FAILED = "failed";
}
