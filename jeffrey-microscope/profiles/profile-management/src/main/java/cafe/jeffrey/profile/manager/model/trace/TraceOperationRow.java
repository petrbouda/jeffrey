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
