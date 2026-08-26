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

import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * The shape of an <em>instant</em>: something the application did once, at a point in time, inside a
 * trace.
 * <p>
 * An instant is the other half of what a trace is made of. A span — {@link AbstractTracedEvent} — is
 * an interval: it has a name, a kind, an outcome, a duration, and a place in a tree. An instant has
 * none of those. It marks a moment and says what happened at it, and the only structural thing it
 * carries is which span was open at the time.
 * <p>
 * That difference is why the two do not share a base class. Hoisting them together would give every
 * instant a duration nobody sets and a status nobody reads, and would put a zero-width bar in the
 * waterfall under every span that ever said anything.
 *
 * <h2>Identity</h2>
 * The ids follow the same convention as a span's, for the same reasons: plain {@code long}s, because
 * JFR varint-encodes integral fields while every distinct string enters the per-chunk constant pool;
 * and {@code 0} meaning "absent", which is also the cheapest varint encoding, so an instant recorded
 * outside any trace costs practically nothing over its untraced shape.
 * <p>
 * {@link Contextual} on the id fields does nothing for Jeffrey's own analysis. It is there so that
 * {@code jfr print} and JDK Mission Control show the trace and span id next to the instant, for
 * anyone opening the recording in another tool.
 *
 * <h2>Why the field is not called {@code spanId}</h2>
 * The derivation discovers which event types are spans structurally, by looking for a declared
 * {@code spanId} column, so that an event type instrumented later takes part in traces with no
 * change to the reader. An instant is not a span -- it has no name, no kind, no outcome and no
 * duration, and drawing one in the waterfall would put a zero-width bar under every span that ever
 * said anything. Naming the field {@code enclosingSpanId} keeps it out of that discovery while
 * giving the instant query a predicate of the same shape.
 * <p>
 * {@link TraceScopeEvent} names its own field {@code scopedSpanId} for the same reason, but it is
 * <em>not</em> an instant and deliberately does not extend this class: a scope carries a duration —
 * it is the stretch of time during which a span was active on one thread — so it is an interval that
 * merely happens not to be a span.
 *
 * @see AbstractTracedEvent
 * @see EventAttributes
 */
public abstract class AbstractTracedInstant extends Event {

    /**
     * The trace that was open when this fired, or {@code 0} when there was none -- the same
     * "no id" convention {@link SpanContext} uses.
     */
    @Contextual
    @Label("Trace Id")
    @Description("The trace that was open when the instant fired, 0 when there was none")
    public long traceId;

    /**
     * The innermost span that was open when this fired, or {@code 0} when there was none.
     * Deliberately not named {@code spanId} -- see the class javadoc.
     */
    @Contextual
    @Label("Enclosing Span Id")
    @Description("The span that was open when the instant fired; not named spanId, an instant is not a span")
    public long enclosingSpanId;

    /**
     * Operation-specific detail, as the JSON object {@link EventAttributes} builds -- the same field,
     * the same encoding and the same builder a span's attributes use, so one reader renders both and
     * one index searches both.
     * <p>
     * What belongs here is per-occurrence detail: the pool that ran dry, the tenant it happened to,
     * the threshold that was crossed. What does not is anything the event already declares as a
     * field of its own.
     */
    @Label("Attributes")
    @Description("Instant-specific detail, encoded as a JSON object")
    public String attributes;

    /**
     * Stamps the enclosing span's ids onto the event and commits it.
     *
     * <p>Ids already set by the caller are left alone, so an instant raised on one thread for work
     * that belongs to another can carry the context it was handed rather than the context it
     * happens to be running in.
     */
    public final void emit() {
        if (traceId == 0 && enclosingSpanId == 0) {
            Tracer.current().ifPresent(context -> {
                traceId = context.traceId();
                enclosingSpanId = context.spanId();
            });
        }
        commit();
    }
}
