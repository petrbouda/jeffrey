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
 * One stage of a running (or finished) pipeline.
 *
 * @param id         stage identifier shared with the frontend's pipeline definition
 * @param status     where the stage has got to
 * @param durationMs elapsed milliseconds once terminal, else {@code null}
 * @param elapsedMs  milliseconds spent so far while {@link StageStatus#IN_PROGRESS}, measured with the
 *                   backend clock so a reconnecting frontend can resume the stage timer without
 *                   client/server clock skew; else {@code null}
 * @param subPhases  fine-grained timing breakdown when available, else {@code null}
 */
public record StageProgress(
        String id,
        StageStatus status,
        Long durationMs,
        Long elapsedMs,
        List<SubPhaseTiming> subPhases
) {

    static StageProgress pending(String id) {
        return new StageProgress(id, StageStatus.PENDING, null, null, null);
    }
}
