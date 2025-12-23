/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr.chunk;

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
