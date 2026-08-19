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
 * One value of one key, with the traces that carried it summarised.
 * <p>
 * A trace counts towards every value of the key it carried — a trace whose spans recorded two
 * tenants appears under both. That is a property of per-span attributes rather than a defect, and it
 * is why these counts do not sum to the profile's trace count.
 *
 * @param value        the value, as text; a number is still text here, so one row type serves every
 *                     kind of key
 * @param traceCount   how many traces carried it
 * @param totalNanos   their total duration — what the list ranks by
 * @param p50Nanos     median trace duration
 * @param p95Nanos     95th percentile trace duration
 * @param maxNanos     the slowest of them
 * @param errorTraces  how many of them had at least one failed span
 */
public record TraceAttributeValueRecord(
        String value,
        long traceCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long maxNanos,
        long errorTraces) {
}
