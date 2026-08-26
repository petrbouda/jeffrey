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

package cafe.jeffrey.shared.notification;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.EventAttributes;

/**
 * How Jeffrey says something about its own work, into its own recording.
 *
 * <pre>{@code
 * Notifications.of(NotificationType.RECORDING_DELETED)
 *         .attribute("recordingId", recordingId)
 *         .attribute("hadProfile", recording.hasProfile())
 *         .emit();
 * }</pre>
 *
 * <h2>Why a builder rather than a method per event</h2>
 * The alternative is a class of static factories, one per kind, which is how the hub's emitter is
 * written. That shape puts the whole vocabulary in one file, but it also drags every caller's domain
 * — pipelines, chunks, guards, downloads — into a module that should not know about any of them, and
 * it makes the attribute map an afterthought assembled behind the call. Here the call site owns its
 * own detail, and the vocabulary stays in {@link NotificationType}.
 *
 * <h2>What goes where, and why the split matters</h2>
 * The type carries everything constant — category, severity and the message — and the call site adds
 * only what differs between two occurrences: ids, counts, durations, the class that was thrown.
 * <p>
 * That split is what keeps a notification cheap. JFR interns every distinct string in its per-chunk
 * constant pool, so a constant message costs one entry however often it is raised, while a sentence
 * assembled per call costs a new entry every time. The same split decides what is <em>queryable</em>:
 * attributes are flattened into the profile's searchable index, one row per distinct value, so an id
 * put in an attribute can be searched and the same id spliced into a message cannot.
 * <p>
 * There is deliberately no way to set the message here — see {@link NotificationType}.
 *
 * <h2>Cost when nothing is recording</h2>
 * {@link #of} checks {@link NotificationEvent#isEnabled()} once and hands back a builder that does
 * nothing at all when the event type is off, so a caller pays for neither the attribute JSON nor the
 * strings it would have held. Emitting is safe from any thread and never throws: a notification is
 * commentary on work, and must not be able to fail the work it is commenting on.
 */
public final class Notifications {

    /** Shared by every disabled call, since it holds no state and does nothing. */
    private static final Builder DISABLED = new Builder(null, null);

    private Notifications() {
    }

    /**
     * Begins a notification of one kind, or a builder that discards everything when the event type is
     * not being recorded.
     *
     * <p>The type is the only thing to choose: its category, severity and message all come with it,
     * so two occurrences of a kind cannot disagree about how serious it is or what it says.
     *
     * @param type what kind of thing happened
     */
    public static Builder of(NotificationType type) {
        NotificationEvent event = new NotificationEvent();
        if (!event.isEnabled()) {
            return DISABLED;
        }
        event.type = type.name();
        event.category = type.category().name();
        event.severity = type.severity().name();
        event.message = type.message();
        return new Builder(event, EventAttributes.create());
    }

    /**
     * One notification under construction.
     *
     * <p>Not thread-safe and single-use, like the event it fills. A builder handed back by
     * {@link #of} when recording is off carries no event and drops every call.
     */
    public static final class Builder {

        private final NotificationEvent event;
        private final EventAttributes attributes;
        private boolean hasAttributes;

        private Builder(NotificationEvent event, EventAttributes attributes) {
            this.event = event;
            this.attributes = attributes;
        }

        /** Which component raised it, when that is not obvious from the type. */
        public Builder source(String source) {
            if (event != null) {
                event.source = source;
            }
            return this;
        }

        /**
         * Pins the notification to a span that is no longer bound on this thread.
         *
         * <p>{@link #emit()} normally takes the enclosing span from the ambient context, which is
         * right for a notification raised in the middle of some work. It is wrong for one raised
         * about work that has just <em>finished</em>: by then the scope has closed and the ambient
         * context is empty again, so the notification would land in no trace at all and the waterfall
         * would not draw it against the bar it is describing. The span event still carries its ids
         * after the scope ends, so hand it in here and they are copied over instead.
         */
        public Builder inSpanOf(AbstractTracedEvent span) {
            if (event != null && span != null) {
                event.traceId = span.traceId;
                event.enclosingSpanId = span.spanId;
            }
            return this;
        }

        /**
         * Attributes a null value as JSON {@code null} rather than dropping the key: "this was not
         * set" and "this was never recorded" are different answers, and only the first is a fact.
         */
        public Builder attribute(String key, String value) {
            if (event != null) {
                attributes.put(key, value);
                hasAttributes = true;
            }
            return this;
        }

        public Builder attribute(String key, long value) {
            if (event != null) {
                attributes.put(key, value);
                hasAttributes = true;
            }
            return this;
        }

        public Builder attribute(String key, double value) {
            if (event != null) {
                attributes.put(key, value);
                hasAttributes = true;
            }
            return this;
        }

        public Builder attribute(String key, boolean value) {
            if (event != null) {
                attributes.put(key, value);
                hasAttributes = true;
            }
            return this;
        }

        /**
         * The class of a failure, which is the one part of an exception worth indexing — a message
         * carries the ids of one occurrence and would make the key unsearchably wide.
         */
        public Builder errorType(Throwable failure) {
            if (event != null && failure != null) {
                attribute("errorType", failure.getClass().getName());
            }
            return this;
        }

        /**
         * Commits the notification against whatever span is open, and never throws.
         *
         * <p>Swallowing here is deliberate and is the one place it is right: this is commentary on
         * work that has already happened, so a fault in the commentary must not become a fault in the
         * work. There is nowhere better to report it either — the failure would be a JFR problem, and
         * JFR is what we would be reporting it through.
         */
        public void emit() {
            if (event == null) {
                return;
            }
            try {
                if (hasAttributes) {
                    event.attributes = attributes.json();
                }
                event.emit();
            } catch (RuntimeException e) {
                // Intentionally ignored -- see the javadoc above.
            }
        }
    }
}
