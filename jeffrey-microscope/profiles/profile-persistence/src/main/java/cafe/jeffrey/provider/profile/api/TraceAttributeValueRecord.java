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
