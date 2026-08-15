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

package cafe.jeffrey.provider.profile.api;

/**
 * How many spans started in one time bucket on one thread — the honest zoomed-out shape of a window
 * too busy to list span by span.
 * <p>
 * The span cap turns a capped window's span list into a biased sample: whichever rows happened to
 * arrive first, drawn as if they were everything. Counting is not subject to the cap, so when the
 * cap bites, this is what the timeline draws instead.
 * <p>
 * Carries the thread's name and kind because in a capped window the density is the <em>only</em>
 * record of some threads — one past the cap has no span row left to say what it was called.
 *
 * @param threadHash  the thread, matching {@link TraceSpanRecord#threadHash()}
 * @param threadName  the thread's recorded name, or null for one the recording never named
 * @param isVirtual   whether the thread was virtual
 * @param bucketIndex which slice of the window, 0-based
 * @param count       spans that started in that slice on that thread
 */
public record TraceSpanDensityRecord(
        long threadHash,
        String threadName,
        boolean isVirtual,
        int bucketIndex,
        long count) {
}
