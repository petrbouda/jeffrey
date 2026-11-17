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
