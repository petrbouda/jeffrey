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

import cafe.jeffrey.jfr.events.trace.AbstractTracedInstant;
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A note the application writes into its own recording: a threshold crossed, a cache warmed, a
 * feature flag flipped, a circuit breaker opened.
 *
 * <p>A notification is an {@link AbstractTracedInstant} -- it has no duration of its own and it is
 * not a span. It merely records which span was open when it fired, so the analysis can draw it
 * against that span rather than guessing from the thread and the clock. The identity fields, the
 * attribute map and {@code emit()} all come from there; what this type adds is the five fields that
 * say what was notified.
 *
 * <p>Use {@code emit()} rather than {@code commit()}: it stamps the trace and enclosing span ids
 * from the enclosing span before committing. A plain {@code commit()} still records the notification;
 * it simply arrives with no trace context and belongs to no trace.
 *
 * <pre>{@code
 * NotificationEvent notification = new NotificationEvent();
 * notification.type = "CIRCUIT_BREAKER_OPEN";
 * notification.message = "5 of the last 6 calls to acme-pay failed. The breaker is open for 30 s.";
 * notification.severity = Severity.CRITICAL.name();
 * notification.category = "AVAILABILITY";
 * notification.source = "payments-client";
 * notification.attributes = EventAttributes.create()
 *         .put("upstream", "acme-pay")
 *         .put("failures", 5)
 *         .put("openForSeconds", 30)
 *         .json();
 * notification.emit();
 * }</pre>
 *
 * <p>The five fields below are the low-cardinality shape every notification has, and every reader
 * leans on: {@link #type} names and groups it, {@link #severity} ranks it, {@link #category} and
 * {@link #source} say what kind of concern it is and who raised it. What varies per occurrence
 * belongs in {@link #message} or in {@code attributes} instead -- see {@link EventAttributes}.
 *
 * <p>There is deliberately no {@code title}. A short human label for a kind of notification is a
 * function of its {@link #type} and nothing else -- the same words every time
 * {@code CONNECTION_POOL_EXHAUSTED} is raised -- so recording one per event pays a constant-pool
 * string to store what the type already said. Naming is the reader's job; what varies per occurrence
 * is {@link #message}.
 *
 * <p>{@link #severity} is the whole of "how serious is this". There is deliberately no second
 * event type for the serious ones: two types carrying the same five fields could disagree with each
 * other, and every reader already ranks by severity.
 *
 * @see AbstractTracedInstant
 * @see cafe.jeffrey.jfr.events.trace.TraceScopeEvent
 */
@Name(NotificationEvent.NAME)
@Label("Notification")
@Description("An application notification, recorded against the span that was open when it fired")
@Category({"Application", "Notification"})
@StackTrace(false)
public class NotificationEvent extends AbstractTracedInstant {

    public static final String NAME = "jeffrey.Notification";

    @Label("Type")
    @Description("Identifier for this kind of notification (e.g. HIGH_CPU_USAGE, CONNECTION_POOL_EXHAUSTED)")
    public String type;

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
}
