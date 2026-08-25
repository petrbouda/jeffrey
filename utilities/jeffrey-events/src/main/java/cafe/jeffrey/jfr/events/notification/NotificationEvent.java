/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.jfr.events.notification;

import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.TraceScopeEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.Category;
import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A note the application writes into its own recording: a threshold crossed, a cache warmed, a
 * feature flag flipped, a circuit breaker opened.
 *
 * <p>A notification is an <em>instant</em>. It has no duration of its own and it is not a span: it
 * does not extend {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent}, and it does not
 * declare a {@code spanId}. It merely records which span was open when it fired, so the analysis
 * can draw it against that span rather than guessing from the thread and the clock.
 *
 * <p>Use {@link #emit()} rather than {@code commit()}: it stamps {@link #traceId} and
 * {@link #enclosingSpanId} from the enclosing span before committing. A plain {@code commit()}
 * still works and still records the notification -- it simply arrives with no trace context, the
 * way it did before the fields existed.
 *
 * <pre>{@code
 * NotificationEvent notification = new NotificationEvent();
 * notification.type = "CIRCUIT_BREAKER_OPEN";
 * notification.title = "Payment circuit breaker opened";
 * notification.message = "5 of the last 6 calls to acme-pay failed. The breaker is open for 30 s.";
 * notification.severity = Severity.CRITICAL.name();
 * notification.category = "AVAILABILITY";
 * notification.source = "payments-client";
 * notification.emit();
 * }</pre>
 *
 * <p>{@link #severity} is the whole of "how serious is this". There is deliberately no second
 * event type for the serious ones: two types carrying the same six fields could disagree with each
 * other, and every reader already ranks by severity.
 *
 * <h2>Why the field is not called {@code spanId}</h2>
 * The derivation discovers which event types are spans structurally, by looking for a declared
 * {@code spanId} column, so that an event type instrumented later takes part in traces with no
 * change to the reader. A notification is not a span -- it has no name, no kind, no outcome and no
 * duration, and drawing one in the waterfall would put a zero-width bar under every span that ever
 * said anything. Naming the field {@code enclosingSpanId} keeps it out of that discovery while
 * giving the notification query a predicate of the same shape. {@link TraceScopeEvent} names its
 * own field {@code scopedSpanId} for exactly this reason.
 *
 * @see cafe.jeffrey.jfr.events.trace.TraceScopeEvent
 */
@Name(NotificationEvent.NAME)
@Label("Notification")
@Description("An application notification, recorded against the span that was open when it fired")
@Category({"Application", "Notification"})
@StackTrace(false)
public class NotificationEvent extends Event {

    public static final String NAME = "jeffrey.Notification";

    /**
     * The trace that was open when this fired, or {@code 0} when there was none -- the same
     * "no id" convention {@link SpanContext} uses.
     */
    @Contextual
    @Label("Trace Id")
    @Description("The trace that was open when the notification fired, 0 when there was none")
    public long traceId;

    /**
     * The innermost span that was open when this fired, or {@code 0} when there was none.
     * Deliberately not named {@code spanId} -- see the class javadoc.
     */
    @Contextual
    @Label("Enclosing Span Id")
    @Description("The span that was open when the notification fired; not named spanId, a notification is not a span")
    public long enclosingSpanId;

    @Label("Type")
    @Description("Identifier for this kind of notification (e.g. HIGH_CPU_USAGE, CONNECTION_POOL_EXHAUSTED)")
    public String type;

    @Label("Title")
    @Description("Short summary of the notification")
    public String title;

    @Label("Message")
    @Description("Detailed description of the notification")
    public String message;

    @Label("Severity")
    @Description("How serious this is: the name of a Severity constant")
    public String severity;

    @Label("Category")
    @Description("The category of the notification (e.g. PERFORMANCE, SECURITY, RESOURCE, AVAILABILITY)")
    public String category;

    @Label("Source")
    @Description("The component or service that raised the notification")
    public String source;

    /**
     * Stamps the enclosing span's ids onto the event and commits it.
     *
     * <p>Ids already set by the caller are left alone, so a notification raised on one thread for
     * work that belongs to another can carry the context it was handed rather than the context it
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
