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

package cafe.jeffrey.shared.ui.workspace.bridge;

import java.util.List;

/**
 * How far a recording's profile has got through initialization, as the recordings list reports it.
 * <p>
 * States and statuses are the wire codes rather than the enums behind them, on purpose. This module
 * is shared with deployments that have no profile pipeline at all — the hub wires
 * {@link RecordingProfileInfoProvider#NOOP} — so it cannot depend on the pipeline's types, and a
 * deployment that does have them maps at the boundary.
 *
 * @param state  the run's state code, or {@code null} when this recording has no run to report
 * @param stages one entry per stage the pipeline declares, in the order it runs them
 */
public record ProfileInitProgress(String state, List<Stage> stages) {

    /**
     * @param id         the stage's stable id, which the frontend maps to a label
     * @param status     the stage's status code
     * @param durationMs how long the stage took, once it has finished
     * @param elapsedMs  how long it has been running, while it is the one in progress
     */
    public record Stage(String id, String status, Long durationMs, Long elapsedMs) {
    }

    /** Nothing to report: no profile, or no run recorded for it. */
    public static final ProfileInitProgress NONE = new ProfileInitProgress(null, List.of());

    public ProfileInitProgress {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
