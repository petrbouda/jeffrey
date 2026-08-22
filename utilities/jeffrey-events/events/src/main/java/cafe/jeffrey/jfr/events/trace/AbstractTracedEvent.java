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
 * The shape of a span, carried by every event that can take part in a trace.
 * <p>
 * An event that extends this <em>is</em> a span once it carries trace identity — there is nothing
 * left for a consumer to interpret. That is deliberate: the alternative is a reader that knows how
 * each event type spells its name, its kind and its outcome, which has to be edited every time an
 * event type is instrumented and silently omits the new one until someone does. Here the event
 * describes itself, and everything downstream reads one uniform shape.
 * <p>
 * What stays type-specific is presentation, not structure: a JDBC statement's SQL and row count are
 * its own declared fields, and it is the UI that chooses to draw them differently from an HTTP
 * exchange's URI.
 *
 * <h2>Identity</h2>
 * The ids are plain {@code long}s rather than strings on purpose: JFR varint-encodes integral
 * fields, while every distinct string value enters the per-chunk constant pool. Trace and span ids
 * are the highest-cardinality values an event can carry, so encoding them as strings is the single
 * biggest recording-size risk in a tracing setup.
 * <p>
 * A value of {@code 0} means "absent" — zero is also the cheapest varint encoding, so an event that
 * never takes part in a trace costs practically nothing over its untraced shape. A span with
 * {@code parentSpanId == 0} is a root span.
 * <p>
 * The trace id is 64-bit, not the 128-bit shape used by W3C Trace Context and OpenTelemetry.
 * Jeffrey mints every id itself within a single recording, where 64 bits is far more than enough;
 * the trade-off is that an externally supplied 128-bit trace id cannot be stored without loss.
 * <p>
 * {@link Contextual} on the id fields does nothing for Jeffrey's own analysis — it reconstructs the
 * span-to-event association from the thread and the time window. It is there so that {@code jfr
 * print} and JDK Mission Control show the trace and span id next to every lock, I/O and exception
 * event that occurred inside the span, for anyone opening the recording in another tool.
 */
public abstract class AbstractTracedEvent extends Event {

    @Label("Trace Id")
    @Contextual
    public long traceId;

    @Label("Span Id")
    @Contextual
    public long spanId;

    @Label("Parent Span Id")
    public long parentSpanId;

    @Label("Name")
    @Description("Operation name; must be a stable, low-cardinality label")
    public String name;

    @Label("Kind")
    public String kind = SpanKind.INTERNAL.name();

    @Label("Status")
    public String status = SpanStatus.UNSET.name();

    @Label("Error Type")
    @Description("Class name of the exception that ended the span, when it ended in an error")
    public String errorType;

    @Label("Attributes")
    @Description("Operation-specific detail, encoded as a JSON object")
    public String attributes;

    /**
     * Records that the operation this event describes threw, which is what makes it count as a
     * failure in the trace it belongs to: the span status becomes {@link SpanStatus#ERROR} and the
     * exception's class name is kept as the error type.
     * <p>
     * This is the one way a failure is stated, for every event family alike — a JDBC statement that
     * threw, an HTTP call that never got a response, a gRPC call torn down by the transport. Call it
     * on the exception path and rethrow; never assign {@link #status} directly.
     * <p>
     * A recorded failure survives {@link #describeSpan()}: an event type that derives its verdict
     * from its own fields — an HTTP exchange from its status code — only ever escalates to
     * {@link SpanStatus#ERROR}, so a transport failure recorded here is not painted over by a
     * status code that was never received.
     *
     * <pre>{@code
     * try {
     *     response = execution.execute(request, body);
     * } catch (IOException e) {
     *     event.failed(e);
     *     throw e;
     * }
     * }</pre>
     */
    public final void failed(Throwable failure) {
        this.status = SpanStatus.ERROR.name();
        this.errorType = failure.getClass().getName();
    }

    /**
     * Fills in the span shape from the event's own fields, called once immediately before the event
     * is committed.
     * <p>
     * This is the hook an instrumented event overrides to say how it names itself and how its own
     * notion of success maps onto {@link SpanStatus} — an HTTP exchange is named by its method and
     * matched URI template and fails at status 400, a gRPC call is named by service and method and
     * fails at anything but {@code OK}. Doing it here rather than at the emitting call site means an
     * event type answers for its own span shape in one place.
     * <p>
     * Events whose fields are already the span shape — a hand-written {@link TraceSpanEvent}, a JDBC
     * statement named after the statement it runs — leave this alone.
     */
    protected void describeSpan() {
    }

    /**
     * The one way an instrumented event is committed: stamps the event into the trace in progress
     * when it does not yet carry identity, describes the span, and commits. One verb for every
     * emitter, whatever the event's role in the trace — which matters because the historical split
     * between a stamping and a non-stamping commit is exactly how every heap-dump statement once
     * went missing from the traces it ran inside, with nothing failing to say so.
     * <ul>
     *   <li><b>A leaf committed in its own {@code finally}</b> — a JDBC statement, an outbound HTTP
     *       call — is stamped here, at commit time, as a child of the span in progress. Stamping
     *       late rather than at construction costs nothing (the enclosing binding is stack-scoped,
     *       so the ids are identical at both points) and an event that falls under the JFR
     *       threshold never pays for minting ids it will not record.</li>
     *   <li><b>An event that already carries identity</b> — one that <em>is</em> its span
     *       ({@link Tracer#inSpanOf}, {@link Tracer#openSpanOf}), or a deferred emitter that
     *       stamped eagerly with {@link Tracer#stamp} — is committed as it is. Re-stamping would
     *       mint a fresh span id and orphan everything recorded under the original one.</li>
     *   <li><b>No span in progress</b> — the ids stay {@code 0}: the event is recorded, feeds its
     *       dashboard, and is simply not part of any trace.</li>
     * </ul>
     * The one emit shape that needs a second call is a deferred commit: an event committed from a
     * stream's {@code close()} may run after the enclosing span's binding is gone, or inside
     * someone else's, so such an emitter must stamp eagerly with {@link Tracer#stamp} at
     * construction — this method then commits it under the identity it already carries.
     * <p>
     * The inherited {@link #commit()} remains the raw path: it neither stamps nor runs
     * {@link #describeSpan()}, so reach for it only for an event that must deliberately stay
     * outside the trace in progress.
     */
    public final void commitSpan() {
        if (spanId == 0) {
            Tracer.stamp(this);
        }
        describeSpan();
        commit();
    }

    /**
     * @deprecated {@link #commitSpan()} now stamps an unstamped event exactly as this method did,
     * so the two commit paths collapsed into one; this alias only delegates. It stays through the
     * 0.x line — removing published API in a minor release would break call sites for no gain —
     * and goes at the next major.
     */
    @Deprecated(since = "0.14.0", forRemoval = true)
    public final void stampAndCommit() {
        commitSpan();
    }
}
