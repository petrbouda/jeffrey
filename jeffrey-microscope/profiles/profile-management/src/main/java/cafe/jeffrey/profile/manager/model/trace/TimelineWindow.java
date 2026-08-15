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
 * Everything the unified timeline draws for one viewport: what stopped the JVM, and what each thread
 * was doing.
 * <p>
 * Fetched per viewport rather than once for the recording. A capture can hold millions of spans and
 * the reader is looking at a few thousand of them, so the window is the unit of work — which is what
 * makes pan and zoom affordable at all.
 *
 * @param truncated whether the span cap was reached. Said out loud rather than left implicit: a
 *                  timeline that quietly stops drawing looks like a JVM that quietly stopped
 *                  working, and the reader has no way to tell the two apart
 */
public record TimelineWindow(
        long fromEpochMicros,
        long toEpochMicros,
        List<TracePause> pauses,
        List<TimelineTrack> tracks,
        boolean truncated,
        /** Separate from {@code truncated}: the spans being complete says nothing about the states. */
        boolean statesTruncated) {
}
