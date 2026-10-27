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

package cafe.jeffrey.shared.ui.hub.bridge;

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
