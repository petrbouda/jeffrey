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
 * Latency of one trace type across the profile — the aggregate view that answers "which kind of
 * request is generally slow", as opposed to "which single trace was slow".
 * <p>
 * A trace type is identified by its root span's name. Nested spans are not types of their own: they
 * are explored through the trace's span tree, not through this list.
 * <p>
 * {@code eventType} is the event that opened the trace — which instrumentation the operation came
 * from, something the name alone does not say.
 * <p>
 * Every percentile here is aggregated over the whole type, so they can be read against each other.
 * A p99 taken from the capped trace list beside a p95 taken from the table would be two different
 * questions in one row, which is why the view showed no p99 at all until this one existed.
 * <p>
 * {@code notificationCount} is how many notifications the application raised inside traces of this
 * type, and {@code urgentNotificationCount} how many of those were {@code CRITICAL} or {@code HIGH}
 * — the operation's own account of what went wrong, beside the latency that shows what it cost.
 */
public record TraceOperationRow(
        String name,
        String kind,
        String eventType,
        long count,
        long errorCount,
        long notificationCount,
        long urgentNotificationCount,
        long spanCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos) {
}
