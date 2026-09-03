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

package cafe.jeffrey.provider.profile.api;

/**
 * One trace type, aggregated over every trace of it.
 *
 * @param notificationCount       how many notifications the application raised inside traces of this
 *                                type, of any severity
 * @param urgentNotificationCount how many of those were {@code CRITICAL} or {@code HIGH}
 */
public record TraceOperationRecord(
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
