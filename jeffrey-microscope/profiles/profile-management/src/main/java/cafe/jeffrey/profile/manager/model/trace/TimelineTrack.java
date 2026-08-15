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

import java.util.List;

/**
 * One thread's row on the unified timeline, with the spans that ran on it inside the window.
 *
 * @param threadHash the join key back to the threads table; {@code "0"} for a span whose thread did
 *                   not resolve, which is kept as its own track rather than dropped
 * @param laneCount  how many depth lanes the track needs — one more than the deepest span, or zero
 *                   when the thread has no spans, so the view can size the row before drawing it
 */
public record TimelineTrack(
        String threadHash,
        String threadName,
        boolean isVirtual,
        int laneCount,
        List<TimelineSpan> spans) {
}
