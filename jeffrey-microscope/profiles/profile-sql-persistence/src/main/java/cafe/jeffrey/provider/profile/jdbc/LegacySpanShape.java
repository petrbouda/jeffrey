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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.model.EventTypeName;

/**
 * The span shape of a recording made before an event carried one.
 * <p>
 * {@code AbstractTracedEvent} gained {@code name}, {@code kind} and {@code status} in the same
 * change that made the derivation a flat projection, so every event now describes its own span and
 * {@link JdbcTraceRepository} needs to know nothing about any event type. A recording taken before
 * that change carries the trace ids and the event's own fields and nothing else: an HTTP exchange
 * recorded its method, its URI and an integer {@code status}, and left the span shape to be worked
 * out by whoever read it.
 * <p>
 * Read with the current projection alone, such an exchange has no name and no kind, so it falls
 * back to its event type — every request in the recording collapses into one operation called
 * {@code jeffrey.HttpServerExchange}, and the Trace Operations view lists event types instead of
 * operations. These expressions are the derivation that used to do that work, kept as a fallback
 * consulted only when the event did not describe itself.
 * <p>
 * Everything in Jeffrey that names an instrumented event type lives here rather than in the
 * repository, and by construction this list never grows: a type instrumented from now on records
 * its own shape. Only recordings that already exist can need an entry, and no new one can appear.
 *
 * <h2>Telling the two apart</h2>
 * {@code kind} is the discriminator. Every event that describes itself carries one — the base class
 * defaults it to {@link cafe.jeffrey.jfr.events.trace.SpanKind#INTERNAL}, so it is never absent —
 * while none of the older shapes had the field at all. That matters most for {@code status}, which
 * is not merely missing in the older shape but means something else: an HTTP exchange's
 * {@code status} was the response code, and reading 200 as a span status would file every legacy
 * request under an outcome that is not one of {@link cafe.jeffrey.jfr.events.trace.SpanStatus}'s.
 */
final class LegacySpanShape {

    /** The lowest response code the exchange counts as a failure — the same 400 the event uses. */
    private static final int FIRST_ERROR_STATUS = 400;

    /** What the event recorded once it described its own span, and the column read when it did. */
    private static final String RECORDED_NAME = "json_extract_string(fields, '$.name')";
    private static final String RECORDED_KIND = "json_extract_string(fields, '$.kind')";
    private static final String RECORDED_STATUS = "json_extract_string(fields, '$.status')";
    private static final String EVENT_TYPE = "event_type";

    /**
     * How an exchange named itself before the event did: the same method-and-URI and
     * service-and-method conventions {@code describeSpan()} now applies in the JVM.
     */
    //language=SQL
    private static final String LEGACY_NAME = """
            CASE %s
                     WHEN '%s' THEN json_extract_string(fields, '$.method') || ' ' || json_extract_string(fields, '$.uri')
                     WHEN '%s' THEN json_extract_string(fields, '$.method') || ' ' || json_extract_string(fields, '$.uri')
                     WHEN '%s' THEN json_extract_string(fields, '$.service') || '/' || json_extract_string(fields, '$.method')
                     WHEN '%s' THEN json_extract_string(fields, '$.service') || '/' || json_extract_string(fields, '$.method')
                 END"""
            .formatted(
                    EVENT_TYPE,
                    EventTypeName.HTTP_SERVER_EXCHANGE,
                    EventTypeName.HTTP_CLIENT_EXCHANGE,
                    EventTypeName.GRPC_SERVER_EXCHANGE,
                    EventTypeName.GRPC_CLIENT_EXCHANGE);

    /**
     * An exchange's direction was a property of the event type rather than a recorded field, which
     * is what the constructor of each event class now settles.
     * <p>
     * The last arm covers a JDBC statement, which is recognised by the flag it recorded instead of a
     * status rather than by naming its six event types: a statement is a call out to something else,
     * so it can only be a CLIENT span.
     */
    //language=SQL
    private static final String LEGACY_KIND = """
            CASE
                     WHEN %s IN ('%s', '%s') THEN 'SERVER'
                     WHEN %s IN ('%s', '%s') THEN 'CLIENT'
                     WHEN json_extract_string(fields, '$.isSuccess') IS NOT NULL THEN 'CLIENT'
                 END"""
            .formatted(
                    EVENT_TYPE, EventTypeName.HTTP_SERVER_EXCHANGE, EventTypeName.GRPC_SERVER_EXCHANGE,
                    EVENT_TYPE, EventTypeName.HTTP_CLIENT_EXCHANGE, EventTypeName.GRPC_CLIENT_EXCHANGE);

    /**
     * What the event's own notion of an outcome meant before {@code describeSpan()} mapped it onto a
     * span status: an HTTP response code, a gRPC status code, or a JDBC success flag.
     * <p>
     * The first arm is what keeps this off an event that describes itself. Unlike the name and the
     * kind, {@code status} is present in both shapes with two different meanings, so a plain
     * {@code COALESCE} on the recorded value would read a modern span's status here and a legacy
     * exchange's response code as if it were one.
     */
    //language=SQL
    private static final String LEGACY_STATUS = """
            CASE
                     WHEN %s IS NOT NULL THEN NULL
                     WHEN %s IN ('%s', '%s')
                         THEN CASE WHEN TRY_CAST(%s AS BIGINT) >= %d THEN 'ERROR' ELSE 'UNSET' END
                     WHEN %s IN ('%s', '%s')
                         THEN CASE WHEN %s = 'OK' THEN 'OK' ELSE 'ERROR' END
                     WHEN json_extract_string(fields, '$.isSuccess') = 'false' THEN 'ERROR'
                 END"""
            .formatted(
                    RECORDED_KIND,
                    EVENT_TYPE, EventTypeName.HTTP_SERVER_EXCHANGE, EventTypeName.HTTP_CLIENT_EXCHANGE,
                    RECORDED_STATUS, FIRST_ERROR_STATUS,
                    EVENT_TYPE, EventTypeName.GRPC_SERVER_EXCHANGE, EventTypeName.GRPC_CLIENT_EXCHANGE,
                    RECORDED_STATUS);

    private LegacySpanShape() {
    }

    /**
     * The span name: what the event recorded, else what the older shape of that event type says, and
     * only then the event type itself — which is a last resort meaning "this carried trace ids and
     * nothing that names it", not a name any operation should be listed under.
     */
    static String nameProjection() {
        return "COALESCE(%s, %s, %s)".formatted(RECORDED_NAME, LEGACY_NAME, EVENT_TYPE);
    }

    /** The span kind, defaulting to the neutral one for an event that says nothing about it. */
    static String kindProjection() {
        return "COALESCE(%s, %s, 'INTERNAL')".formatted(RECORDED_KIND, LEGACY_KIND);
    }

    /**
     * The span status. The legacy reading comes first, not last: it is the arm that knows the
     * recorded {@code status} is a response code rather than a span status.
     */
    static String statusProjection() {
        return "COALESCE(%s, %s, 'UNSET')".formatted(LEGACY_STATUS, RECORDED_STATUS);
    }
}
