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
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

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
 *   <li><b>A span must begin and end on the same thread.</b> JFR attributes a duration event to the
 *       thread that commits it, so a span handed off mid-flight is recorded against the wrong
 *       thread — which in turn breaks the thread-plus-time-window correlation Jeffrey uses to show
 *       what the JVM was doing inside the span. Use {@link #continueIn} to open a <em>separate</em>
 *       child span on the receiving thread rather than carrying one across the boundary.</li>
 *   <li><b>{@link ScopedValue} propagates to child threads only through structured concurrency.</b>
 *       Work submitted to a plain executor does not inherit the current span; pass the
 *       {@link SpanContext} explicitly and re-establish it with {@link #continueIn}.</li>
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
     * Opens a span for {@code body} without emitting a {@link TraceSpanEvent}, for instrumentation
     * whose <em>own</em> event already describes the interval — an HTTP exchange, a gRPC call, a
     * JDBC statement. Emitting a trace span alongside such an event would record the same interval
     * twice.
     * <p>
     * The caller stamps its own event with {@link #stamp(AbstractTracedEvent)} from inside
     * {@code body}, and anything nested inside it is parented correctly for free:
     *
     * <pre>{@code
     * Tracer.inSpan(() -> {
     *     event.begin();
     *     try {
     *         chain.doFilter(request, response);
     *     } finally {
     *         event.end();
     *         if (event.shouldCommit()) {
     *             Tracer.stamp(event);
     *             event.commit();
     *         }
     *     }
     *     return null;
     * });
     * }</pre>
     * <p>
     * Unlike {@link #call}, this always establishes a binding: whether the interval is recorded is
     * the caller's event's decision, not this method's.
     */
    public static <R, X extends Throwable> R inSpan(ScopedValue.CallableOp<? extends R, X> body) throws X {
        Objects.requireNonNull(body, "body must not be null");
        return ScopedValue.where(CURRENT, newSpanContext()).call(body);
    }

    /**
     * Opens a span for {@code body} and stamps it onto {@code event} — the whole of the
     * "my own event is the span" pattern in one call, and the form to prefer over pairing
     * {@link #inSpan} with {@link #stamp} by hand.
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
     *         event.status = response.getStatus();
     *         event.commit();
     *     }
     * }
     * }</pre>
     */
    public static <R, X extends Throwable> R inSpanOf(
            AbstractTracedEvent event, ScopedValue.CallableOp<? extends R, X> body) throws X {

        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(body, "body must not be null");
        return inSpan(() -> {
            stamp(event);
            return body.call();
        });
    }

    /**
     * Copies the span currently in progress onto {@code event}, so the event takes its place in the
     * trace. Does nothing when no span is in progress, leaving the event's ids at {@code 0} — the
     * encoding for "not part of a trace".
     */
    public static void stamp(AbstractTracedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (CURRENT.isBound()) {
            stamp(event, CURRENT.get());
        }
    }

    private static void stamp(AbstractTracedEvent event, SpanContext context) {
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

        TraceSpanEvent event = new TraceSpanEvent();
        if (!event.isEnabled()) {
            return body.call();
        }
        return record(event, name, kind, childOf(parent), body);
    }

    /**
     * Derives the context for a span about to start on this thread: a child of whatever is bound,
     * or a fresh root when nothing is.
     */
    private static SpanContext newSpanContext() {
        return childOf(CURRENT.isBound() ? CURRENT.get() : null);
    }

    private static SpanContext childOf(SpanContext parent) {
        RandomGenerator random = ThreadLocalRandom.current();
        return parent == null ? SpanContext.root(random) : parent.child(random);
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
                stamp(event, context);
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
