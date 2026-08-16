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
 * One slice of the recording, with what the traces starting inside it did.
 * <p>
 * Aggregated in SQL rather than by bucketing a fetched list, because the list the trace page fetches
 * is the slowest N — bucketing that would draw the density of slow traces and label it the density of
 * traces. Only buckets that hold at least one trace are returned; a gap in the result is a stretch of
 * the recording with no traces in it, which the chart draws as zero.
 *
 * @param fromMillisFromBeginning where the bucket starts, relative to the recording's start
 * @param count                   how many traces started inside it
 * @param errorCount              how many of those had at least one failed span
 * @param maxDurationNanos        the slowest of them, which is what makes a spike visible at all —
 *                                an average over a busy bucket hides the one trace worth opening
 */
public record TraceTimelineBucketRecord(
        long fromMillisFromBeginning,
        long count,
        long errorCount,
        long maxDurationNanos) {
}
