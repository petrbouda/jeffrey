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

package cafe.jeffrey.shared.volume.layout;

import java.util.Optional;

/**
 * An instance with how many sessions it holds and its newest one.
 *
 * @param sessionCount the instance's session markers
 * @param newest       its newest session, or empty while it has none
 */
public record InstanceSummary(InstanceDirectory instance, int sessionCount, Optional<VolumeSession> newest) {

    public InstanceSummary {
        if (instance == null || newest == null || sessionCount < 0) {
            throw new IllegalArgumentException("An instance summary needs its instance, a count and its newest session");
        }
        if (newest.isEmpty() != (sessionCount == 0)) {
            throw new IllegalArgumentException(
                    "An instance has a newest session exactly when it has sessions: count=" + sessionCount);
        }
    }
}
