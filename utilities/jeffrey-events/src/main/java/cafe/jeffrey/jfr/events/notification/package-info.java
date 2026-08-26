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

/**
 * Notes an application writes into its own recording, so that "what the process thought was
 * happening" sits next to the samples and events that show what actually did.
 *
 * <p>{@link cafe.jeffrey.jfr.events.notification.NotificationEvent} ({@code jeffrey.Notification})
 * carries a stable {@code type} (an identifier such as {@code CONNECTION_POOL_EXHAUSTED} --
 * screaming snake case, one per kind of notification, never per occurrence), a detailed
 * {@code message}, a {@code severity} (the name of a
 * {@link cafe.jeffrey.jfr.events.notification.Severity} constant), a {@code category} and the
 * {@code source} component that raised it.
 *
 * <pre>{@code
 * NotificationEvent notification = new NotificationEvent();
 * notification.type = "EVENT_PROCESSING_FAILED";
 * notification.message = "Chunk 42 of profile.jfr could not be parsed: " + e.getMessage();
 * notification.severity = Severity.HIGH.name();
 * notification.category = "AVAILABILITY";
 * notification.source = "recording-ingestion";
 * notification.attributes = EventAttributes.create()
 *         .put("recording", "profile.jfr")
 *         .put("chunk", 42)
 *         .json();
 * notification.emit();
 * }</pre>
 *
 * <p>{@code type} is the notification's name as well as its identity: there is deliberately no
 * separate {@code title}, because a short label for a kind of notification is a function of its type
 * and nothing else, and recording one per event stores what the type already said.
 *
 * <p>The five fields above are the shape every notification has and every reader leans on, so they
 * stay low-cardinality. What varies per occurrence -- the chunk that failed, the pool that ran dry
 * -- goes in {@code message} or in {@code attributes}, the same open JSON map a span carries and the same
 * {@link cafe.jeffrey.jfr.events.trace.EventAttributes} builder fills. Both the map and the six
 * fields are searchable from Traces by Attributes.
 *
 * <p>These are instants, not spans: they mark a moment and carry no duration of their own. They do
 * take part in traces, though -- {@code emit()} stamps the enclosing span's {@code traceId} and
 * {@code spanId} onto the event, so the analysis can draw a notification against the span that
 * raised it instead of inferring it from the thread and the clock. A notification committed with no
 * span open simply carries zeroes and belongs to no trace.
 *
 * <p>{@code severity} is the whole of "how serious is this". There is deliberately no second event
 * type for the serious ones.
 */
package cafe.jeffrey.jfr.events.notification;
