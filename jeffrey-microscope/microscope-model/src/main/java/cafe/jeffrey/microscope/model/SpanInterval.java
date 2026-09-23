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

package cafe.jeffrey.microscope.model;

/**
 * An interval of work reduced to what is needed to scope samples to it: the identity hash of the
 * thread it ran on plus its absolute time window. A sample belongs to the interval only if it was
 * taken on {@code threadHash} between {@code fromEpochMillis} and {@code toEpochMillis}.
 * {@code thread_hash} is used (not the OS id) so the match works for virtual threads too.
 * <p>
 * Deliberately neutral about what produced the interval. Both span features feed it: an
 * async-profiler tag contributes one interval per {@code profiler.Span} it covers, and a trace span
 * contributes its own window, or that window minus its children's when scoped to self time. This is
 * the one thing the two share — the SQL predicate that turns a window into a sample filter.
 */
public record SpanInterval(long threadHash, long fromEpochMillis, long toEpochMillis) {

    public SpanInterval {
        if (toEpochMillis < fromEpochMillis) {
            throw new IllegalArgumentException(
                    "Span interval end before start: from=" + fromEpochMillis + " to=" + toEpochMillis);
        }
    }
}
