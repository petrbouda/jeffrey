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
 */
public record TraceOperationRow(
        String name,
        String kind,
        long count,
        long errorCount,
        long spanCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long maxNanos) {
}
