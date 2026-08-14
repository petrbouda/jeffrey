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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Records nested spans into the JFR recording.
 * <p>
 * The span currently in progress is published through a {@link ScopedValue}, so a nested
 * {@link #call} or {@link #run} discovers its parent without the caller threading anything through.
 * The binding is bounded by the lambda, which means it cannot outlive the span and never needs to be
 * cleared.
 *
 * <pre>{@code
 * Tracer.run("order.checkout", SpanKind.SERVER, () -> {
 *     Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
 *     Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
 * });
 * }</pre>
 *
 * <h2>Cost when nothing is recording</h2>
 * When the {@link TraceSpanEvent} type is disabled — no JFR recording, or a configuration that
 * leaves the event off — the body runs directly with no binding established and no event allocated
 * beyond the (escape-analysable) event instance itself. Instrumentation is therefore safe to leave
 * in production code.
 *
 * <h2>Limits</h2>
 * <ul>
 *   <li><b>A span's own event names the thread that ended it.</b> JFR attributes a duration event to
 *       the thread that commits it, so a span closed somewhere other than where it opened is filed
 *       against the closing thread. For work that is a separate operation on the receiving thread,
 *       prefer {@link #continueIn}, which opens a child span there and stays thread-confined. For
 *       one operation arriving in pieces — a protocol's callbacks — use {@link #reenter}, which
 *       emits a {@link TraceScopeEvent} per activation so the threads the span really ran on are
 *       recorded even though its own event can only name one.</li>
 *   <li><b>{@link ScopedValue} propagates to child threads only through structured concurrency.</b>
 *       Work submitted to a plain executor does not inherit the current span; wrap the task with
 *       {@link #fork}, or pass the {@link SpanContext} explicitly and re-establish it with
 *       {@link #continueIn}.</li>
 *   <li><b>Span names must be stable and low-cardinality.</b> Every distinct name enters the JFR
 *       per-chunk string pool, so a name built from a request id or a user id inflates the
 *       recording. Name the operation, not the instance of it.</li>
 * </ul>
 */
public final class Tracer {

    private static final ScopedValue<SpanContext> CURRENT = ScopedValue.newInstance();

    private Tracer() {
    }

    /**
     * @return the span currently in progress on this thread, or empty when none is
     */
    public static Optional<SpanContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /**
     * Records a span around {@code body}, with kind {@link SpanKind#INTERNAL}.
     */
    public static void run(String name, Runnable body) {
        run(name, SpanKind.INTERNAL, body);
    }

    /**
     * Records a span around {@code body}.
     */
    public static void run(String name, SpanKind kind, Runnable body) {
        Objects.requireNonNull(body, "body must not be null");
        call(name, kind, () -> {
            body.run();
            return null;
        });
    }

    /**
     * Records a span around {@code body}, with kind {@link SpanKind#INTERNAL}, and returns its result.
     */
    public static <R, X extends Throwable> R call(String name, ScopedValue.CallableOp<? extends R, X> body) throws X {
        return call(name, SpanKind.INTERNAL, body);
    }

    /**
     * Records a span around {@code body} and returns its result.
     * <p>
     * An exception escaping {@code body} marks the span {@link SpanStatus#ERROR}, records the
     * exception's class name, and is rethrown unchanged.
     *
     * @param name a stable, low-cardinality operation name
     * @param kind what role the operation plays
     * @param body the work the span covers
     */
    public static <R, X extends Throwable> R call(
            String name, SpanKind kind, ScopedValue.CallableOp<? extends R, X> body) throws X {

        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(body, "body must not be null");

        TraceSpanEvent event = new TraceSpanEvent();
        if (!event.isEnabled()) {
            return body.call();
        }
        return record(event, name, kind, newSpanContext(), body);
    }

    /**
     * Opens a span and makes {@code event} that span, for instrumentation whose own event already
     * describes the interval — an HTTP exchange, a gRPC call. Emitting a {@link TraceSpanEvent}
     * alongside such an event would record the same interval twice, so none is emitted; the event
     * carries the ids, and everything traced inside {@code body} nests underneath it.
     * <p>
     * The ids are stamped when the span opens, not when the event commits: they are known up front,
     * and the {@link ScopedValue} binding is gone by the time a caller's {@code finally} block runs,
     * so stamping there would silently do nothing.
     *
     * <pre>{@code
     * event.begin();
     * try {
     *     Tracer.inSpanOf(event, () -> {
     *         chain.doFilter(request, response);
     *         return null;
     *     });
     * } finally {
     *     event.end();
     *     if (event.shouldCommit()) {
     *         event.statusCode = response.getStatus();
     *         event.commitSpan();
     *     }
     * }
     * }</pre>
     */
    public static <R, X extends Throwable> R inSpanOf(
            AbstractTracedEvent event, ScopedValue.CallableOp<? extends R, X> body) throws X {

        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(body, "body must not be null");

        // Binds directly rather than going through reenter(): this opens the span, so it is already
        // its own single scope, and a TraceScopeEvent here would record the same interval twice.
        return ScopedValue.where(CURRENT, openSpanOf(event)).call(body);
    }

    /**
     * The {@link Runnable} form of {@link #inSpanOf(AbstractTracedEvent, ScopedValue.CallableOp)},
     * for a body with no result and no checked exception.
     */
    public static void inSpanOf(AbstractTracedEvent event, Runnable body) {
        Objects.requireNonNull(body, "body must not be null");
        inSpanOf(event, () -> {
            body.run();
            return null;
        });
    }

    /**
     * Opens a span on {@code event} and hands back its context <em>without</em> binding it, for
     * instrumentation that cannot wrap its work in a lambda.
     * <p>
     * A callback-driven protocol is the case this exists for: a gRPC call runs from listener
     * callbacks long after the interceptor that started it has returned, so there is no single block
     * for {@link #inSpanOf} to enclose. The caller stamps the event here, keeps the returned context,
     * and re-establishes it per callback with {@link #reenter}.
     * <p>
     * Nothing is bound on the calling thread, so a caller that forgets to re-enter loses the nesting
     * — not the span. The event still carries its identity and still appears in the trace.
     */
    public static SpanContext openSpanOf(AbstractTracedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        SpanContext context = newSpanContext();
        stampSelf(event, context);
        return context;
    }

    /**
     * Re-establishes an existing span on this thread for the duration of {@code body}, so work that
     * belongs to a span already opened elsewhere nests underneath it.
     * <p>
     * Distinct from {@link #continueIn}, which mints a <em>child</em> and emits a span event for it:
     * this rebinds the very same span, because a protocol's callbacks are not separate operations,
     * they are the same operation arriving in pieces.
     * <p>
     * Each re-entry emits a {@link TraceScopeEvent} recording which thread the span ran on and for
     * how long. That is not bookkeeping for its own sake: once a span is re-entered it can be closed
     * on a thread it never really ran on, and the scope events are then the only record of where the
     * work actually happened.
     *
     * <pre>{@code
     * SpanContext context = Tracer.openSpanOf(event);
     * // ... later, on whatever thread the protocol calls back on:
     * Tracer.reenter(context, () -> {
     *     delegate().onMessage(message);
     *     return null;
     * });
     * }</pre>
     */
    public static <R, X extends Throwable> R reenter(
            SpanContext context, ScopedValue.CallableOp<? extends R, X> body) throws X {

        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(body, "body must not be null");

        TraceScopeEvent event = new TraceScopeEvent();
        if (!event.isEnabled()) {
            // Bind regardless, as continueIn does: the caller re-enters precisely because this thread
            // has nothing bound, so skipping it would drop the work out of the trace entirely.
            return ScopedValue.where(CURRENT, context).call(body);
        }

        event.begin();
        try {
            return ScopedValue.where(CURRENT, context).call(body);
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.traceId = context.traceId();
                event.scopedSpanId = context.spanId();
                event.commit();
            }
        }
    }

    /**
     * The {@link Runnable} form of {@link #reenter(SpanContext, ScopedValue.CallableOp)}, for a
     * callback with no result and no checked exception.
     */
    public static void reenter(SpanContext context, Runnable body) {
        Objects.requireNonNull(body, "body must not be null");
        reenter(context, () -> {
            body.run();
            return null;
        });
    }

    /**
     * Gives {@code event} a span of its own, nested inside the span currently in progress, so the
     * event takes its place in the trace. Does nothing when no span is in progress, leaving the
     * event's ids at {@code 0} — the encoding for "not part of a trace".
     * <p>
     * A span of its own, rather than a copy of the enclosing one: every statement issued inside one
     * span would otherwise carry that same span id, and a span id has to identify exactly one span.
     * Nothing is bound to the minted id, which is what makes a stamped event a leaf — the work it
     * describes is the event itself, not a scope other spans nest inside.
     */
    public static void stamp(AbstractTracedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (CURRENT.isBound()) {
            stampSelf(event, CURRENT.get().child());
        }
    }

    /**
     * Stamps the event with a context as its own, for a span the event <em>is</em> rather than one
     * it hangs off.
     */
    private static void stampSelf(AbstractTracedEvent event, SpanContext context) {
        event.traceId = context.traceId();
        event.spanId = context.spanId();
        event.parentSpanId = context.parentSpanId();
    }

    /**
     * Records a span whose parent is {@code parent} rather than whatever this thread currently has
     * bound — the way to keep a trace connected across an executor boundary, where
     * {@link ScopedValue} does not propagate on its own.
     * <p>
     * Pass {@code null} to start a fresh trace.
     */
    public static <R, X extends Throwable> R continueIn(
            SpanContext parent, String name, SpanKind kind, ScopedValue.CallableOp<? extends R, X> body) throws X {

        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SpanContext context = childOf(parent);

        TraceSpanEvent event = new TraceSpanEvent();
        if (!event.isEnabled()) {
            // Still bind, unlike call(): the caller handed us a context precisely because this thread
            // has none, so returning without one would drop the forked work out of the trace and
            // no-op every stamp underneath it. There is nothing to fall back on here, which is not
            // true of call(), where an enclosing binding survives.
            return ScopedValue.where(CURRENT, context).call(body);
        }
        return record(event, name, kind, context, body);
    }

    /**
     * The form of {@link #continueIn(SpanContext, String, SpanKind, ScopedValue.CallableOp)} with
     * kind {@link SpanKind#INTERNAL} — forked work is in-process work unless declared otherwise.
     */
    public static <R, X extends Throwable> R continueIn(
            SpanContext parent, String name, ScopedValue.CallableOp<? extends R, X> body) throws X {
        return continueIn(parent, name, SpanKind.INTERNAL, body);
    }

    /**
     * The {@link Runnable} form of
     * {@link #continueIn(SpanContext, String, SpanKind, ScopedValue.CallableOp)}, for a task with no
     * result and no checked exception.
     */
    public static void continueIn(SpanContext parent, String name, SpanKind kind, Runnable body) {
        Objects.requireNonNull(body, "body must not be null");
        continueIn(parent, name, kind, () -> {
            body.run();
            return null;
        });
    }

    /**
     * The {@link Runnable} form of {@link #continueIn(SpanContext, String, SpanKind, Runnable)},
     * with kind {@link SpanKind#INTERNAL}.
     */
    public static void continueIn(SpanContext parent, String name, Runnable body) {
        continueIn(parent, name, SpanKind.INTERNAL, body);
    }

    /**
     * Wraps {@code body} so that, wherever it eventually runs, it is recorded as a child of the span
     * in progress <em>here</em> — the packaged form of capturing {@link #current} before an executor
     * hand-off and re-establishing it inside the task with {@link #continueIn}.
     * <p>
     * The capture happens when this method is called, not when the task runs, so wrap on the thread
     * whose span the work belongs to and submit the result. Called outside any span, the task starts
     * a fresh trace — the same fallback {@link #continueIn} has for a {@code null} parent.
     *
     * <pre>{@code
     * CompletableFuture.runAsync(
     *         Tracer.fork("chunk.parse", () -> parseChunk(file)),
     *         executor);
     * }</pre>
     */
    public static Runnable fork(String name, SpanKind kind, Runnable body) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SpanContext parent = CURRENT.isBound() ? CURRENT.get() : null;
        return () -> continueIn(parent, name, kind, body);
    }

    /**
     * The form of {@link #fork(String, SpanKind, Runnable)} with kind {@link SpanKind#INTERNAL}.
     */
    public static Runnable fork(String name, Runnable body) {
        return fork(name, SpanKind.INTERNAL, body);
    }

    /**
     * The value-returning form of {@link #fork(String, SpanKind, Runnable)}, shaped as a
     * {@link Supplier} so it hands straight to {@code CompletableFuture.supplyAsync}.
     */
    public static <T> Supplier<T> fork(String name, SpanKind kind, Supplier<T> body) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SpanContext parent = CURRENT.isBound() ? CURRENT.get() : null;
        return () -> continueIn(parent, name, kind, body::get);
    }

    /**
     * The form of {@link #fork(String, SpanKind, Supplier)} with kind {@link SpanKind#INTERNAL}.
     */
    public static <T> Supplier<T> fork(String name, Supplier<T> body) {
        return fork(name, SpanKind.INTERNAL, body);
    }

    /**
     * Derives the context for a span about to start on this thread: a child of whatever is bound,
     * or a fresh root when nothing is.
     */
    private static SpanContext newSpanContext() {
        return childOf(CURRENT.isBound() ? CURRENT.get() : null);
    }

    private static SpanContext childOf(SpanContext parent) {
        return parent == null ? SpanContext.root() : parent.child();
    }

    private static <R, X extends Throwable> R record(
            TraceSpanEvent event,
            String name,
            SpanKind kind,
            SpanContext context,
            ScopedValue.CallableOp<? extends R, X> body) throws X {

        Throwable failure = null;
        event.begin();
        try {
            return ScopedValue.where(CURRENT, context).call(body);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            event.end();
            if (event.shouldCommit()) {
                stampSelf(event, context);
                event.name = name;
                event.kind = kind.name();
                if (failure == null) {
                    event.status = SpanStatus.UNSET.name();
                } else {
                    event.status = SpanStatus.ERROR.name();
                    event.errorType = failure.getClass().getName();
                }
                event.commit();
            }
        }
    }
}
