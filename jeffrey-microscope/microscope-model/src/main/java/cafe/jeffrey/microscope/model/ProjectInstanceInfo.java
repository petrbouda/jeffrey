/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model;

import java.time.Instant;

/**
 * An instance as the volume describes it.
 *
 * @param startedAt    when the provisioner registered it
 * @param lastDataAt   when the data of its newest session ends, or {@code null} while it has none
 * @param sessionCount how many sessions it holds
 */
public record ProjectInstanceInfo(
        String id,
        String projectId,
        String instanceName,
        Instant startedAt,
        Instant lastDataAt,
        int sessionCount) {
}
