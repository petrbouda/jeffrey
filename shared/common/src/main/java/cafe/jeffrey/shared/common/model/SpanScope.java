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

package cafe.jeffrey.shared.common.model;

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
