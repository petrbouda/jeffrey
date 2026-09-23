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

package cafe.jeffrey.profile.manager.model.blocking;

/**
 * Headline metrics for the Blocking Operations page — application thread blocking: lock contention
 * ({@code jdk.JavaMonitorEnter}), {@code Object.wait()} ({@code jdk.JavaMonitorWait}), parks
 * ({@code jdk.ThreadPark}), sleeps ({@code jdk.ThreadSleep}) and virtual-thread pinning.
 *
 * @param contendedMonitorCount    distinct monitor classes with contended enters
 * @param totalMonitorBlockedNanos summed blocked time across contended monitor enters
 * @param waitCount                number of {@code Object.wait()} events
 * @param parkCount                number of {@code jdk.ThreadPark} events
 * @param sleepCount               number of {@code jdk.ThreadSleep} events
 * @param pinnedCount              number of {@code jdk.VirtualThreadPinned} events
 * @param hasMonitorEnter          whether contended-monitor events are present
 * @param hasMonitorWaits          whether {@code Object.wait()} events are present
 * @param hasParks                 whether thread-park events are present
 * @param hasSleeps                whether {@code Thread.sleep()} events are present
 * @param hasPinned                whether virtual-thread-pinned events are present
 */
public record BlockingOverview(
        long contendedMonitorCount,
        long totalMonitorBlockedNanos,
        long waitCount,
        long parkCount,
        long sleepCount,
        long pinnedCount,
        boolean hasMonitorEnter,
        boolean hasMonitorWaits,
        boolean hasParks,
        boolean hasSleeps,
        boolean hasPinned) {
}
