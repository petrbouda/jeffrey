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
import java.util.function.Consumer;

/**
 * The emit shape of a leaf event, written once: guard, {@code begin}, the work, {@code end} on
 * success or {@link AbstractTracedEvent#failed(Throwable) failed} on the exception path,
 * {@code shouldCommit}, fill, {@link AbstractTracedEvent#commitSpan() commitSpan}.
 * <p>
 * Every emitter of a statement or client-call event repeats the same ten lines, and each repeat
 * must remember the same three rules: {@code end()} is not called when the body throws
 * (committing closes the interval itself), fields are filled only inside the
 * {@code shouldCommit()} block so an event under threshold pays nothing for them, and the failure
 * goes through {@code failed(t)} before the rethrow. This class is those rules in one place, so an
 * emit site says only what is specific to it — which event, which work, which fields:
 *
 * <pre>{@code
 * JdbcQueryEvent event = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
 * List<User> users = TracedEvents.emit(event,
 *         () -> delegate.query(sql, rowMapper),
 *         (e, result) -> {
 *             e.sql = sql;
 *             e.rows = result != null ? result.size() : 0;
 *         });
 * }</pre>
 *
 * The filler receives the event with its concrete type, so type-specific fields ({@code sql},
 * {@code statusCode}, {@code isBatch}) assign without casts. On the exception path the filler
 * still runs — the statement's label and SQL belong on a failed event too — with a {@code null}
 * result, which is what the result stands for when the work never produced one.
 * <p>
 * When nothing is recording, the body runs directly: no {@code begin}, no filler, nothing
 * allocated beyond the (escape-analysable) event instance itself.
 * <p>
 * This is the shape of a <em>leaf</em> committed in its own {@code finally} — the event is stamped
 * by {@code commitSpan()} as a child of the span in progress. It is not for an event that is its
 * own span: an inbound exchange keeps the {@link Tracer#inSpanOf} form, whose body runs
 * <em>inside</em> the span the event opens.
 */
public final class TracedEvents {

    /**
     * The work an emitted event covers. Declared with its own throwable type so a checked
     * exception propagates through {@link #emit(AbstractTracedEvent, Body, Filler)} unchanged,
     * exactly as the JDK's {@code ScopedValue.CallableOp} does for {@link Tracer#call}.
     */
    @FunctionalInterface
    public interface Body<R, X extends Throwable> {

        R call() throws X;
    }

    /**
     * The {@link Body} of an emit with no result.
     */
    @FunctionalInterface
    public interface VoidBody<X extends Throwable> {

        void run() throws X;
    }

    /**
     * Fills the event's type-specific fields immediately before it commits — called only when the
     * event is actually committing, and on the failure path called with a {@code null} result.
     */
    @FunctionalInterface
    public interface Filler<E extends AbstractTracedEvent, R> {

        void fill(E event, R result);
    }

    private TracedEvents() {
    }

    /**
     * Runs {@code body} inside {@code event}'s interval and commits the event as a leaf span,
     * returning the body's result.
     * <p>
     * An exception escaping {@code body} is recorded with
     * {@link AbstractTracedEvent#failed(Throwable)} and rethrown unchanged; the filler then
     * receives a {@code null} result.
     *
     * @param event  the event describing the work; freshly constructed, not yet begun
     * @param body   the work the event covers
     * @param filler fills the event's fields when — and only when — it commits
     */
    public static <E extends AbstractTracedEvent, R, X extends Throwable> R emit(
            E event, Body<? extends R, X> body, Filler<? super E, ? super R> filler) throws X {

        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(filler, "filler must not be null");

        if (!event.isEnabled()) {
            return body.call();
        }

        event.begin();
        R result = null;
        try {
            result = body.call();
            // Deliberately only on the success path: when the body throws, commit() closes the
            // interval itself, and the failure is what failed(t) records below.
            event.end();
            return result;
        } catch (Throwable t) {
            event.failed(t);
            throw t;
        } finally {
            if (event.shouldCommit()) {
                filler.fill(event, result);
                event.commitSpan();
            }
        }
    }

    /**
     * The form of {@link #emit(AbstractTracedEvent, Body, Filler)} for work with no result; the
     * filler receives only the event.
     */
    public static <E extends AbstractTracedEvent, X extends Throwable> void emit(
            E event, VoidBody<X> body, Consumer<? super E> filler) throws X {

        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(filler, "filler must not be null");

        emit(event, () -> {
            body.run();
            return null;
        }, (e, result) -> filler.accept(e));
    }
}
