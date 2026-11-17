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

package cafe.jeffrey.provider.profile.api;

/**
 * Profile-wide trace totals.
 *
 * @param notificationCount       how many notifications the application raised inside traces, of
 *                                any severity; a notification outside every trace is not counted
 * @param urgentNotificationCount how many of those were {@code CRITICAL} or {@code HIGH}
 */
public record TraceOverviewRecord(
        long totalTraces,
        long totalSpans,
        long errorTraces,
        long errorSpans,
        long notificationCount,
        long urgentNotificationCount,
        long avgNanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos,
        long totalNanos,
        int distinctOperations) {

    /** What an untraced profile reports: every counter zero rather than a null-riddled row. */
    public static final TraceOverviewRecord EMPTY =
            new TraceOverviewRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
