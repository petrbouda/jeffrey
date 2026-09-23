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

import java.util.List;

/**
 * Which stretches of which threads a query is narrowed to — the answer to "whose samples are these".
 * <p>
 * Two shapes, because a scope arrives in two genuinely different ways and one of them cannot be
 * turned into the other. {@link Intervals} is a handful of windows the caller already holds, and for
 * the profiler-span views it is the <em>only</em> form there is: the window arrives in the request
 * body and exists nowhere in the recording. {@link Operation} names a trace type and lets the
 * database work its windows out, because materialising them would mean carrying one window per trace
 * out of DuckDB and shipping every one of them back in the next statement — a busy operation has
 * hundreds of thousands, and the statement that carried them was twenty megabytes of SQL text that
 * took the better part of a minute to plan.
 * <p>
 * So: a scope that the caller knows travels as values, a scope that the database knows stays in the
 * database, and the query that consumes them cannot tell the difference.
 */
public sealed interface SpanScope {

    /** Whether the scope selects nothing, in which case a query is left unscoped as it always was. */
    boolean isEmpty();

    /**
     * Windows the caller already holds — one profiler-span tag's occurrences, one trace span and its
     * children, or a window named directly in a request.
     */
    record Intervals(List<SpanInterval> intervals) implements SpanScope {

        public Intervals {
            intervals = List.copyOf(intervals);
        }

        @Override
        public boolean isEmpty() {
            return intervals.isEmpty();
        }
    }

    /**
     * Every window every trace of one operation occupied, named rather than enumerated. All three
     * columns identify the type — see {@code TraceOperationId} for why the name alone does not.
     */
    record Operation(String name, String kind, String eventType) implements SpanScope {

        /**
         * An operation always describes a scope, even one no trace matches. Whether it selects any
         * window is a question for the query, not for the caller holding the name.
         */
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    static SpanScope of(List<SpanInterval> intervals) {
        return new Intervals(intervals);
    }
}
