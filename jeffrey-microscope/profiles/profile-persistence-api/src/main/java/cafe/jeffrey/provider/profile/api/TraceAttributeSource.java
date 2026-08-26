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

package cafe.jeffrey.provider.profile.api;

/**
 * Where an attribute key came from. Kept apart rather than merged into one flat namespace because
 * they mean different things to whoever reads them: one was attached by hand, one is the event
 * type's own schema, and one is a column the carrier already had.
 * <p>
 * The source also decides <em>which</em> carrier the key belongs to, and so which index table holds
 * it — see {@link #carrier()}. That is what keeps the two namespaces from crossing: a search for
 * {@link #SPAN_SHAPE} {@code status = ERROR} can never match a notification that merely said so,
 * because the source is part of every predicate the search builds.
 * <p>
 * The names are stored in {@code trace_span_attributes.source} and
 * {@code trace_notification_attributes.source}, and read back out of profiles that outlive the code
 * that wrote them, so they are frozen: never renamed, only added to.
 */
public enum TraceAttributeSource {

    /** The open map from {@code AbstractTracedEvent.attributes} — whatever the developer passed. */
    ATTRIBUTE(TraceAttributeCarrier.SPAN),

    /**
     * A field the event type declares about itself. Qualified by its owning event type: {@code rows}
     * on a JDBC query and {@code rows} on anything else are not the same key.
     */
    EVENT_FIELD(TraceAttributeCarrier.SPAN),

    /** A column every span already has — {@code name}, {@code kind}, {@code status} and the rest. */
    SPAN_SHAPE(TraceAttributeCarrier.SPAN),

    /** The open map from {@code AbstractTracedInstant.attributes} on a notification. */
    NOTIFICATION_ATTRIBUTE(TraceAttributeCarrier.NOTIFICATION),

    /**
     * A column every notification already has — {@code type}, {@code title}, {@code message},
     * {@code severity}, {@code category} and {@code source}.
     * <p>
     * Note that one of those keys is itself called {@code source}, so a row for it reads
     * {@code source = 'NOTIFICATION_SHAPE' AND attr_key = 'source'}. The discriminator and the key
     * are different things; the key is spelled the way the event spells it, so that what the search
     * offers matches what the detail panel showed.
     */
    NOTIFICATION_SHAPE(TraceAttributeCarrier.NOTIFICATION);

    private final TraceAttributeCarrier carrier;

    TraceAttributeSource(TraceAttributeCarrier carrier) {
        this.carrier = carrier;
    }

    /** What kind of thing carries a key from this source, and so which index table holds it. */
    public TraceAttributeCarrier carrier() {
        return carrier;
    }
}
