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
 * Profile-wide trace totals: how much tracing the recording captured, and how slow it was.
 * <p>
 * Traces and spans are counted separately because they fail separately — a handful of failed traces
 * can carry many failed spans, or one failed span can fail an otherwise healthy trace — and the
 * summary is only worth showing if it distinguishes the two.
 *
 * @param totalTraces        traces in the profile
 * @param totalSpans         spans across all of them
 * @param errorTraces        traces containing at least one failed span
 * @param errorSpans         failed spans, however they are distributed across traces
 * @param avgNanos           mean trace duration
 * @param p95Nanos           95th percentile trace duration
 * @param p99Nanos           99th percentile trace duration
 * @param maxNanos           slowest trace
 * @param totalNanos         summed trace duration across the profile
 * @param distinctOperations distinct trace types, matching what the Traces by Operation view ranks
 */
public record TraceOverviewRecord(
        long totalTraces,
        long totalSpans,
        long errorTraces,
        long errorSpans,
        long avgNanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos,
        long totalNanos,
        int distinctOperations) {

    /** What an untraced profile reports: every counter zero rather than a null-riddled row. */
    public static final TraceOverviewRecord EMPTY =
            new TraceOverviewRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
