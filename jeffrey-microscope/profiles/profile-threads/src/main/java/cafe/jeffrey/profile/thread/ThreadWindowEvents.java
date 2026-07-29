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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * What one category of a timeline lane was doing during a hovered slice of time.
 *
 * <p>This is deliberately <em>not</em> a description of a band. A band is a rendering device: it
 * merges every run of activity whose gaps are too small to draw, so on a busy lane a single band
 * can span the whole recording and its {@code eventCount} is that lane's total. Reading a tooltip
 * off a band therefore shows the same number wherever the pointer is. A window is the slice of time
 * actually under the pointer, so the count and the sample events change as it moves.
 *
 * @param fromOffset  start of the window the server resolved, in nanoseconds from the beginning of
 *                    the recording. Events are stored at millisecond resolution, so this is the
 *                    requested start snapped down to a whole millisecond — not necessarily what was
 *                    asked for. Render it rather than the requested window, or a count will look
 *                    finer-grained than it is
 * @param toOffset    end of the resolved window, exclusive. Adjacent windows therefore tile without
 *                    counting an event twice
 * @param eventCount  how many events of the category start inside the window. An event belongs to
 *                    the window it <em>starts</em> in, which is also how the bands are built, so
 *                    per-window counts add up to the lane's total
 * @param events      the first few of those events, capped by the query's limit, for the field rows
 *                    of a tooltip. Never the whole window
 */
public record ThreadWindowEvents(
        long fromOffset,
        long toOffset,
        long eventCount,
        List<ThreadEventDetail> events) {

    public ThreadWindowEvents {
        if (toOffset <= fromOffset) {
            throw new IllegalArgumentException(
                    "Window must be a non-empty range: fromOffset=" + fromOffset + " toOffset=" + toOffset);
        }
        if (eventCount < 0) {
            throw new IllegalArgumentException("Event count must not be negative: " + eventCount);
        }
        events = events == null ? List.of() : List.copyOf(events);
        if (eventCount < events.size()) {
            throw new IllegalArgumentException(
                    "Sample cannot be larger than the count it is drawn from: eventCount=" + eventCount
                            + " events=" + events.size());
        }
    }
}
