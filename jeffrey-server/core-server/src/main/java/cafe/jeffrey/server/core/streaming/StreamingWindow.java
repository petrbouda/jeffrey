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

package cafe.jeffrey.server.core.streaming;

import java.time.Instant;

/**
 * Time window for filtering events during replay streaming.
 * Both bounds are optional — null means unbounded in that direction.
 *
 * @param startTime inclusive lower bound (null = from beginning)
 * @param endTime   inclusive upper bound (null = to latest event)
 */
public record StreamingWindow(Instant startTime, Instant endTime) {

    public StreamingWindow {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be strictly before endTime");
        }
    }

    /** Unbounded window — matches all events. */
    public static final StreamingWindow UNBOUNDED = new StreamingWindow(null, null);

    /**
     * Returns true if the given event time falls within this window.
     */
    public boolean contains(Instant eventTime) {
        if (startTime != null && eventTime.isBefore(startTime)) {
            return false;
        }
        return endTime == null || !eventTime.isAfter(endTime);
    }
}
