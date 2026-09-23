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

package cafe.jeffrey.subsecond.db.api;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.subsecond.db.SubSecondConfig;

/**
 * Generate a data-file for a sub-second Graph from a selected event from JFR file.
 */
public interface SubSecondGenerator {

    /**
     * Generate a data-file for the sub-second base on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a byte-array representation.
     *
     * @param config all information to generate a sub-second representation of the profiling
     * @return sub-second graph data represented in byte-array format.
     */
    JsonNode generate(SubSecondConfig config);
}
