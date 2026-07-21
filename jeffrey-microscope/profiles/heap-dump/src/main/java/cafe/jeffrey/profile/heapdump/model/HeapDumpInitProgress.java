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
 * Live progress of a server-side heap-dump initialization run, polled by the
 * frontend while the pipeline executes in the background.
 *
 * @param state        {@code idle} (no run for this profile), {@code running},
 *                     {@code completed} or {@code failed}
 * @param errorCode    machine-readable error code when {@code failed}, else {@code null}
 * @param errorMessage human-readable failure description when {@code failed}, else {@code null}
 * @param stages       per-stage live statuses in pipeline order; empty when {@code idle}
 */
public record HeapDumpInitProgress(
        String state,
        String errorCode,
        String errorMessage,
        List<HeapDumpInitStageProgress> stages
) {

    public static final String STATE_IDLE = "idle";

    public static final String STATE_RUNNING = "running";

    public static final String STATE_COMPLETED = "completed";

    public static final String STATE_FAILED = "failed";

    public static HeapDumpInitProgress idle() {
        return new HeapDumpInitProgress(STATE_IDLE, null, null, List.of());
    }
}
