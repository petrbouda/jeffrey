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

package cafe.jeffrey.jfr.events.trace;

import jdk.jfr.Category;
import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * One stretch of time during which a span was active on one thread.
 * <p>
 * A span event answers <em>what happened</em>; this answers <em>where and when it was running</em>.
 * The two come apart as soon as a span is re-entered: JFR attributes a duration event to the thread
 * that <em>commits</em> it, so a span opened by {@link Tracer#openSpanOf} on one thread and closed
 * on another is filed against the closing thread, and the span's own event can no longer say which
 * threads the work actually occupied. Every scope is bounded by a single
 * {@link Tracer#reenter} lambda, so it cannot straddle a thread by construction — which is what
 * makes it a trustworthy join key for "what was the JVM doing while this span was running".
 * <p>
 * Emitted only by {@link Tracer#reenter}. {@link Tracer#call} and {@link Tracer#inSpanOf} are
 * thread-confined already — their span <em>is</em> its own single scope — so emitting one there
 * would record the same interval twice. Instrumentation that never re-enters therefore pays nothing
 * for this event type existing.
 *
 * <h2>Why the field is not called {@code spanId}</h2>
 * The derivation discovers which event types are spans structurally, by looking for a declared
 * {@code spanId} column, so that an event type instrumented later takes part in traces with no
 * change to the reader. A scope is not a span — it has no name, no kind and no outcome, and drawing
 * one in the waterfall would double every re-entered span. Naming the field {@code scopedSpanId}
 * keeps it out of that discovery while giving the scope query a predicate of the same shape.
 *
 * @see Tracer#reenter
 */
@Name(TraceScopeEvent.NAME)
@Label("Trace Scope")
@Description("A stretch of time during which a span was active on one thread")
@Category({"Application", "Tracing"})
@StackTrace(false)
public class TraceScopeEvent extends Event {

    public static final String NAME = "jeffrey.TraceScope";

    @Label("Trace Id")
    @Contextual
    public long traceId;

    @Label("Scoped Span Id")
    @Description("The span that was active; deliberately not named spanId, a scope is not a span")
    public long scopedSpanId;
}
