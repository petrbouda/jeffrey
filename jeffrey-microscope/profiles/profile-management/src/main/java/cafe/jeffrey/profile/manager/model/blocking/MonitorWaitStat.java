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
 * Aggregated {@code Object.wait()} statistics for one monitor class ({@code jdk.JavaMonitorWait}).
 * Unlike {@link ContentionStat} (lock acquisition), waits are intentional blocking; the
 * {@code timedOutCount} highlights waits that hit their timeout rather than being notified, which
 * often points at a missed {@code notify}/{@code notifyAll} or a too-short timeout.
 *
 * @param className     monitor class the threads waited on
 * @param count         number of wait events
 * @param totalNanos    summed wait time
 * @param maxNanos      longest single wait
 * @param threadCount   distinct threads that waited on this class
 * @param timedOutCount number of waits that timed out instead of being notified
 */
public record MonitorWaitStat(
        String className,
        long count,
        long totalNanos,
        long maxNanos,
        int threadCount,
        long timedOutCount) {
}
