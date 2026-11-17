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
 * How many traces of one value landed in one duration bucket — a single cell of the latency
 * heatmap.
 * <p>
 * Buckets are half-decades of nanoseconds: {@code bucket = floor(2 * log10(nanos))}, clamped at both
 * ends so the grid stays a fixed width whatever the recording holds. Half-decades rather than
 * decades because a decade is too coarse to separate a 12 ms population from a 90 ms one, and those
 * are exactly the two a reader is trying to tell apart.
 *
 * @param value      the value the traces carried
 * @param bucket     the half-decade index; the caller renders its bounds
 * @param traceCount how many traces of that value fell in it
 */
public record TraceAttributeLatencyRecord(String value, int bucket, long traceCount) {
}
