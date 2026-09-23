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
 * What the trace list opens with: how much tracing the profile holds, and how slow it was.
 * <p>
 * Profile-wide, unlike {@link TraceRow}, which the list caps — the summary has to describe the whole
 * recording or it would contradict the rows underneath it.
 *
 * @param totalTraces        traces in the profile
 * @param totalSpans         spans across all of them
 * @param errorTraces        traces containing at least one failed span
 * @param errorSpans         failed spans, however they are distributed across traces
 * @param notificationCount  notifications the application raised inside traces, of any severity
 * @param urgentNotificationCount how many of those were {@code CRITICAL} or {@code HIGH}
 * @param avgNanos           mean trace duration
 * @param p95Nanos           95th percentile trace duration
 * @param p99Nanos           99th percentile trace duration
 * @param maxNanos           slowest trace
 * @param totalNanos         summed trace duration across the profile
 * @param distinctOperations distinct trace types, matching what the Traces by Operation view ranks
 */
public record TraceOverview(
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
}
