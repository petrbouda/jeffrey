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
 * Aggregated blocking statistics for one class — either a contended monitor class
 * ({@code jdk.JavaMonitorEnter}) or a park blocker class ({@code jdk.ThreadPark}).
 *
 * @param className   monitor/blocker class
 * @param count       number of blocking events
 * @param totalNanos  summed blocked time
 * @param maxNanos    longest single blocked period
 * @param threadCount distinct threads that blocked on this class
 */
public record ContentionStat(
        String className,
        long count,
        long totalNanos,
        long maxNanos,
        int threadCount) {
}
