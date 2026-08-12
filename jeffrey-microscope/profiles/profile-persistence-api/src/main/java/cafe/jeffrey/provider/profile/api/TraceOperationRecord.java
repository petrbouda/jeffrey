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
 * Latency of one operation name across every trace in the profile — the aggregate view that answers
 * "which operation is generally slow", as opposed to "which single trace was slow".
 *
 * @param name           the operation name
 * @param kind           {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param count          how many spans carried this name
 * @param errorCount     how many of them ended in an error
 * @param totalNanos     summed duration, for ranking by total time rather than by average
 * @param p50Nanos       median duration
 * @param p95Nanos       95th percentile duration
 * @param maxNanos       slowest occurrence
 */
public record TraceOperationRecord(
        String name,
        String kind,
        long count,
        long errorCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long maxNanos) {
}
