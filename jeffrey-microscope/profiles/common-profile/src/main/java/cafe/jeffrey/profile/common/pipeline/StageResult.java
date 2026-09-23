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
