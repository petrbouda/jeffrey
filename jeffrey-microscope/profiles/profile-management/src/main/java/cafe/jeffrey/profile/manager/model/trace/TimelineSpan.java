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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * One span as the unified timeline draws it: a bar on a thread's track, at a nesting depth.
 * <p>
 * The depth is <em>lane</em> depth, not tree depth. It counts how many spans on the same thread
 * enclose this one, which is what decides which row of the track it occupies. It usually equals the
 * span's depth in its trace, and deliberately does not have to: a thread that served two unrelated
 * requests in the window shows both at depth 0, because on that thread neither was inside the other.
 *
 * @param traceId   hex, so the reader can open the trace this span belongs to
 * @param spanId    hex
 * @param depth     how many same-thread spans enclose this one
 */
public record TimelineSpan(
        String traceId,
        String spanId,
        String name,
        String kind,
        String status,
        long startEpochMicros,
        long durationNanos,
        int depth) {
}
