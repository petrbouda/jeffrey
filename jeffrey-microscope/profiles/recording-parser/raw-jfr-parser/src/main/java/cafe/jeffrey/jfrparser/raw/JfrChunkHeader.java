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

package cafe.jeffrey.jfrparser.raw;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Represents JFR chunk header information without event types.
 * Use this for performance-sensitive operations that only need timing info,
 * or as an intermediate step before parsing event types in streaming mode.
 */
public record JfrChunkHeader(
        Instant startTime,
        Duration duration,
        long sizeInBytes,
        long offsetMeta,
        boolean latestChunk) {

    /**
     * Creates a full JfrChunk by adding event types.
     *
     * @param eventTypes set of event type names from metadata
     * @return JfrChunk with all information including event types
     */
    public JfrChunk withEventTypes(Set<String> eventTypes) {
        return new JfrChunk(startTime, duration, sizeInBytes, eventTypes, latestChunk);
    }
}
