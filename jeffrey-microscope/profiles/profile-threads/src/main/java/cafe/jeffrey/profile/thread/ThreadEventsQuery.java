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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.microscope.model.ThreadInfo;

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
