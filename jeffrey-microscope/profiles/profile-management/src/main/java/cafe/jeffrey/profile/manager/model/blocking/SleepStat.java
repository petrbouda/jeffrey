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
 * Aggregated {@code Thread.sleep()} statistics for one thread ({@code jdk.ThreadSleep}). Unlike
 * lock contention or parks, sleeps carry no blocker class — the actionable identity is the thread
 * itself, so events are grouped by thread name. A thread with large total sleep time is usually a
 * polling loop that could be event-driven instead.
 *
 * @param thread          name of the sleeping thread
 * @param count           number of sleep events
 * @param totalSleptNanos summed actual slept time
 * @param maxSleptNanos   longest single sleep
 * @param requestedNanos  summed requested sleep time ({@code time} field); compare with
 *                        {@code totalSleptNanos} to spot oversleeping under load
 */
public record SleepStat(
        String thread,
        long count,
        long totalSleptNanos,
        long maxSleptNanos,
        long requestedNanos) {
}
