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

import cafe.jeffrey.shared.common.model.ThreadInfo;

import java.time.Duration;
import java.util.List;

/**
 * Asks what one category of a timeline lane was doing during a slice of time — what a tooltip needs
 * once the pointer settles.
 *
 * <p>The slice is the pointer's own position, not the band underneath it. A band merges every run of
 * activity too dense to draw apart, so on a busy lane it spans the whole recording; asking about the
 * band would answer the same thing wherever the pointer is.
 *
 * @param threads the threads the lane stands for: one for a plain thread, all of them for a
 *                collapsed lane. A collapsed lane has no thread of its own, so asking by its
 *                identity would match nothing
 * @param state   which category was hovered
 * @param from    start of the hovered window, as an offset from the beginning of the recording. The
 *                server snaps it to the millisecond grid the events are stored at
 * @param to      end of the hovered window, exclusive, as an offset from the beginning of the
 *                recording
 * @param limit   how many events to return for the field rows; a tooltip shows one. The count that
 *                comes back covers the whole window regardless of this cap
 */
public record ThreadEventsQuery(
        List<ThreadInfo> threads,
        ThreadState state,
        Duration from,
        Duration to,
        int limit) {

    public ThreadEventsQuery {
        if (threads == null || threads.isEmpty()) {
            throw new IllegalArgumentException("At least one thread must be specified");
        }
        threads = List.copyOf(threads);
        if (state == null || !state.hasEventDetail()) {
            throw new IllegalArgumentException("Thread state has no event detail: " + state);
        }
        if (from == null || to == null || to.compareTo(from) < 0) {
            throw new IllegalArgumentException("Band must be a non-empty range: from=" + from + " to=" + to);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
    }
}
