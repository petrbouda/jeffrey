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
 * One CFS sampling window that contained CPU throttling, drawn across the waterfall.
 * <p>
 * Kept apart from {@link TracePause} all the way to the client, because the view has to draw it
 * differently to stay honest. A pause band's width <em>is</em> its duration; this band's width is
 * the window, and {@code throttledNanos} is a total that happened somewhere inside it. The waterfall
 * draws it hatched and labels the figure as approximate for exactly that reason, and the why-slow
 * panel leaves it out of its ranking — a window-derived number summed beside measured pauses would
 * make percentages that no longer add up.
 *
 * @param startEpochMicros when the window began, absolute, in the units span starts carry
 * @param endEpochMicros   when the window ended, absolute
 * @param throttledNanos   how long the container was parked somewhere inside the window
 * @param throttledSlices  CFS periods throttled in the window
 * @param elapsedSlices    CFS periods elapsed in the window
 * @param ratioPercent     the share of periods that were throttled — how hard, as opposed to how long
 */
public record TraceThrottleWindow(
        long startEpochMicros,
        long endEpochMicros,
        long throttledNanos,
        long throttledSlices,
        long elapsedSlices,
        double ratioPercent) {
}
