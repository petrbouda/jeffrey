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
 * How much of one span's window went to one kind of waiting.
 * <p>
 * One row per {@code (span, category)} that actually recorded something, so a span that only ever
 * ran has no rows at all rather than a row of zeroes for every category there is.
 * <p>
 * The times are summed event durations, not merged intervals: two blocking events on the same thread
 * cannot overlap — a thread waits on one thing at a time — so summing is exact for a single
 * category. Across categories it is still a sum of disjoint stretches for the same reason. What it
 * is <em>not</em> is comparable to the span's inclusive duration, because a child span's waiting is
 * recorded on the child's own thread window and counted there; compare against self time instead.
 *
 * @param spanId      the span these events happened inside
 * @param category    what the thread was waiting on
 * @param totalNanos  how long, summed across every event of that category in the span's window
 * @param occurrences how many events that was, which separates one long stall from a thousand short
 *                    ones — the same total, very different problems
 */
public record TraceSpanContextRecord(
        long spanId,
        TraceContextCategory category,
        long totalNanos,
        long occurrences) {
}
