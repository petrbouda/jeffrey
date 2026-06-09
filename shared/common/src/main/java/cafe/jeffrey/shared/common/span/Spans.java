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

package cafe.jeffrey.shared.common.span;

import one.profiler.Span;

/**
 * Thin wrapper around the async-profiler {@code one.profiler.Span} API used to record latency spans
 * (tagged {@code [start, end]} intervals) into the JFR recording, correlated with profiling samples.
 * <p>
 * This is the single place in the codebase that depends on async-profiler: every other module calls
 * {@code Spans} and never imports {@code one.profiler.*}. All methods are cheap no-ops when the
 * native async-profiler agent is not attached or no JFR session is running — {@code start()} returns
 * {@code 0} and {@code end(...)} does nothing — so instrumentation is safe to leave in production code.
 * <p>
 * Tags must be <b>stable, low-cardinality</b> labels (e.g. {@code "flamegraph.generate"}); never embed
 * request ids or other high-cardinality values, which would defeat the JFR string-pool deduplication.
 * Spans are flat and per-thread: a span is recorded on whichever thread calls {@link #end}.
 *
 * <pre>{@code
 * long span = Spans.start();
 * try {
 *     doWork();
 * } finally {
 *     Spans.end(span, "flamegraph.generate");
 * }
 * }</pre>
 */
public abstract class Spans {

    /**
     * Marks the start of a span on the current thread.
     *
     * @return a start timestamp to pass to {@link #end} / {@link #endIfProfiled}, or {@code 0} when
     *         the profiler is not running (in which case {@link #end} is a no-op)
     */
    public static long start() {
        return Span.start();
    }

    /**
     * Ends a span opened with {@link #start} and records it with the given tag.
     *
     * @param started the value returned by {@link #start}
     * @param tag     a stable, low-cardinality label
     */
    public static void end(long started, String tag) {
        Span.end(started, tag);
    }

    /**
     * Like {@link #end}, but records the span only if at least one profiling sample landed on this
     * thread while it was open. Use for frequent spans where intervals enclosing no sample add nothing.
     *
     * @param started the value returned by {@link #start}
     * @param tag     a stable, low-cardinality label
     */
    public static void endIfProfiled(long started, String tag) {
        Span.endIfProfiled(started, tag);
    }
}
