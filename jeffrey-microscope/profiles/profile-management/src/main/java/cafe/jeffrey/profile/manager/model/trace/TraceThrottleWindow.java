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
