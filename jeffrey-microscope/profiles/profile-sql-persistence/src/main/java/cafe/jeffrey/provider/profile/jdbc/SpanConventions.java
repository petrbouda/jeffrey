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
 * How a span is directed and judged, and how the three span-shape projections compose, expressed
 * as SQL over the event that recorded it. Naming lives in {@link SpanNameTemplates} — one template
 * per event type, built-in or declared in the recording — and feeds {@link #nameProjection}.
 * <p>
 * An operation is what a trace <em>is</em> — {@code GET /api/internal/profiles/{profileId}}, not
 * {@code jeffrey.HttpServerExchange} — and that mapping is a convention rather than a datum. It is
 * the convention OpenTelemetry states for the same operations: an HTTP exchange is
 * {@code {method} {route}} and fails from 400 upwards, a gRPC call is {@code {service}/{method}}
 * and fails on anything but {@code OK}, a database statement is named after the statement it runs
 * and is always a call out to something else.
 * <p>
 * Applying the convention here, rather than reading back a field, is what makes two recordings of
 * the same endpoint one operation: a recording made by an older library, by a newer one, or by
 * third-party instrumentation that stamps the trace ids and its own fields all land under the same
 * operation.
 * <p>
 * The kind and status arms below are keyed to Jeffrey's own event types, and that is not a gap to
 * close: a <em>verdict</em> is never declared — it is the writer's statement, recorded through
 * {@code commitSpan()}/{@code failed()} — and these arms exist only because Jeffrey's own exchange
 * types are known here, so their codes can be judged on recordings of any vintage. Span
 * <em>discovery</em> stays structural — see {@code JdbcTraceRepository.SPAN_EVENT_TYPES}, which
 * finds a span by its declared {@code spanId} field — so an event type nothing here covers is
 * still a span in every trace; it simply carries the name, kind and status it recorded for
 * itself, and its event type as a last resort.
 *
 * <h2>Reading an outcome without guessing which one it is</h2>
 * {@code statusCode} is the key that unambiguously holds an exchange's own outcome: an HTTP
 * response code, a gRPC status code. Where an event carries no {@code statusCode}, that outcome was
 * recorded under {@code status} — a key that in a self-describing event holds a span status
 * instead. The two are told apart by what the value <em>is</em> rather than by how old the
 * recording is: {@code ERROR} and {@code UNSET} are not codes any exchange reports, and {@code OK}
 * means the same thing read either way.
 * <p>
 * What no code can express is a span the instrumentation itself failed — an exchange that answered
 * 200 after an exception set its {@code errorType}. A recorded status of exactly {@code ERROR}
 * therefore outranks the convention.
 */
final class SpanConventions {

    /** The lowest HTTP response code an exchange counts as a failure — the same 400 the event uses. */
    private static final int FIRST_ERROR_STATUS = 400;

    /** gRPC's own name for "the call succeeded"; every other code is a failed call. */
    private static final String OK_STATUS_CODE = "OK";

    /**
     * The three ways an outcome can be judged, as internal switch keys for {@link #outcomeArm}:
     * a numeric code failing from 400, a textual code failing on anything but {@code OK}, and a
     * success flag failing on {@code false}. Internal on purpose — a verdict is recorded by the
     * writer, never declared in a recording, so these names are not wire format.
     */
    private static final String HTTP_CODE_SEMANTICS = "HTTP_CODE";
    private static final String GRPC_CODE_SEMANTICS = "GRPC_CODE";
    private static final String BOOLEAN_SEMANTICS = "BOOLEAN";

    /**
     * The three words a span status can be. Anything else found under {@code status} is the event's
     * own outcome code, recorded before {@code statusCode} was the key for it.
     */
    private static final String SPAN_STATUS_VALUES = "'OK', 'ERROR', 'UNSET'";

    private static final String EVENT_TYPE = "event_type";
    private static final String RECORDED_NAME = "json_extract_string(fields, '$.name')";
    private static final String RECORDED_KIND = "json_extract_string(fields, '$.kind')";
    private static final String RECORDED_STATUS = "json_extract_string(fields, '$.status')";
    private static final String RECORDED_STATUS_CODE = "json_extract_string(fields, '$.statusCode')";

    /** The flag a statement recorded before it had a status of its own. */
    private static final String SUCCESS_FLAG = "json_extract_string(fields, '$.isSuccess')";

    /**
     * The exchange's own outcome code, under whichever key this recording spelled it. The fallback
     * reads {@code status} only where it cannot be a span status, so an exchange that recorded no
     * code at all yields NULL here instead of being judged on the word {@code UNSET}.
     */
    //language=SQL
    private static final String EXCHANGE_CODE = """
            COALESCE(%s, CASE WHEN %s NOT IN (%s) THEN %s END)"""
            .formatted(RECORDED_STATUS_CODE, RECORDED_STATUS, SPAN_STATUS_VALUES, RECORDED_STATUS);

    private static final String SERVER_EXCHANGES =
            "'%s', '%s'".formatted(EventTypeName.HTTP_SERVER_EXCHANGE, EventTypeName.GRPC_SERVER_EXCHANGE);
    private static final String CLIENT_EXCHANGES =
            "'%s', '%s'".formatted(EventTypeName.HTTP_CLIENT_EXCHANGE, EventTypeName.GRPC_CLIENT_EXCHANGE);
    private static final String HTTP_EXCHANGES =
            "'%s', '%s'".formatted(EventTypeName.HTTP_SERVER_EXCHANGE, EventTypeName.HTTP_CLIENT_EXCHANGE);
    private static final String GRPC_EXCHANGES =
            "'%s', '%s'".formatted(EventTypeName.GRPC_SERVER_EXCHANGE, EventTypeName.GRPC_CLIENT_EXCHANGE);
    private static final String JDBC_STATEMENTS = "'%s', '%s', '%s', '%s', '%s', '%s'".formatted(
            EventTypeName.JDBC_INSERT, EventTypeName.JDBC_UPDATE, EventTypeName.JDBC_DELETE,
            EventTypeName.JDBC_QUERY, EventTypeName.JDBC_EXECUTE, EventTypeName.JDBC_STREAM);

    /**
     * An exchange's direction and a statement's are properties of the event type rather than of the
     * run: a server exchange is always the inbound side, a statement always a call out to something
     * else.
     */
    //language=SQL
    private static final String CONVENTIONAL_KIND = """
            CASE
                     WHEN %s IN (%s) THEN 'SERVER'
                     WHEN %s IN (%s) THEN 'CLIENT'
                     WHEN %s IN (%s) THEN 'CLIENT'
                 END"""
            .formatted(EVENT_TYPE, SERVER_EXCHANGES, EVENT_TYPE, CLIENT_EXCHANGES, EVENT_TYPE, JDBC_STATEMENTS);

    /**
     * What an operation's own outcome says about the span. Every arm is guarded on that outcome
     * having been recorded at all, so an event that reported none falls through to what it did
     * report rather than being declared a failure by an {@code ELSE}.
     */
    //language=SQL
    private static final String CONVENTIONAL_STATUS = """
            CASE
                     WHEN %s IN (%s) THEN %s
                     WHEN %s IN (%s) THEN %s
                     WHEN %s IN (%s) THEN %s
                 END"""
            .formatted(
                    EVENT_TYPE, HTTP_EXCHANGES, outcomeArm(HTTP_CODE_SEMANTICS, EXCHANGE_CODE),
                    EVENT_TYPE, GRPC_EXCHANGES, outcomeArm(GRPC_CODE_SEMANTICS, EXCHANGE_CODE),
                    EVENT_TYPE, JDBC_STATEMENTS, outcomeArm(BOOLEAN_SEMANTICS, SUCCESS_FLAG));

    private SpanConventions() {
    }

    /**
     * The one spelling of what each outcome semantics means, so the three built-in arms above
     * cannot drift apart in how they guard and judge. Every arm is guarded on the code being
     * present — and, for the HTTP semantics, numeric — so a row without the field yields NULL and
     * falls through to the recorded status rather than being judged on nothing.
     */
    private static String outcomeArm(String semantics, String codeExpression) {
        return switch (semantics) {
            case HTTP_CODE_SEMANTICS -> """
                    CASE WHEN TRY_CAST(%s AS BIGINT) IS NOT NULL
                         THEN CASE WHEN TRY_CAST(%s AS BIGINT) >= %d THEN 'ERROR' ELSE 'UNSET' END END"""
                    .formatted(codeExpression, codeExpression, FIRST_ERROR_STATUS);
            case GRPC_CODE_SEMANTICS -> """
                    CASE WHEN %s IS NOT NULL
                         THEN CASE WHEN %s = '%s' THEN 'OK' ELSE 'ERROR' END END"""
                    .formatted(codeExpression, codeExpression, OK_STATUS_CODE);
            case BOOLEAN_SEMANTICS -> """
                    CASE WHEN %s IS NOT NULL
                         THEN CASE WHEN %s = 'false' THEN 'ERROR' ELSE 'UNSET' END END"""
                    .formatted(codeExpression, codeExpression);
            default -> throw new IllegalArgumentException("Unknown outcome semantics: " + semantics);
        };
    }

    /**
     * The span name: the template for the event type ({@link SpanNameTemplates} — built-in or
     * declared in the recording), else the name the event recorded for itself, and only then the
     * event type — a last resort meaning "this carried trace ids and nothing that names it", not
     * a name any operation should be listed under.
     * <p>
     * The template comes before the field because an endpoint has to be the same operation
     * whichever version of the instrumentation recorded it. A self-naming type — a hand-written
     * span, a statement named after itself — has only the field, and it is read unchanged.
     *
     * @param templateCase the CASE over per-event-type templates, from {@link SpanNameTemplates}
     */
    static String nameProjection(String templateCase) {
        return "COALESCE(%s, %s, %s)".formatted(templateCase, RECORDED_NAME, EVENT_TYPE);
    }

    /** The span kind, defaulting to the neutral one for an event no convention places. */
    static String kindProjection() {
        return "COALESCE(%s, %s, 'INTERNAL')".formatted(CONVENTIONAL_KIND, RECORDED_KIND);
    }

    /**
     * The span status: an {@code ERROR} the instrumentation recorded, else what the operation's own
     * outcome says for the exchange types known here, else the recorded status, else the neutral
     * one.
     * <p>
     * {@code ERROR} outranks the convention because it is the one outcome a response code cannot
     * carry: an exchange that threw and still answered 200 knows something its code does not. The
     * same reasoning is why a verdict is never <em>declared</em> the way a name is — it is the
     * writer's statement, recorded through {@code commitSpan()}/{@code failed()}, and an event type
     * outside this class's built-ins is judged solely by what it recorded.
     */
    static String statusProjection() {
        return """
                CASE WHEN %s = 'ERROR' THEN 'ERROR'
                                 ELSE COALESCE(%s, %s, 'UNSET') END"""
                .formatted(RECORDED_STATUS, CONVENTIONAL_STATUS, RECORDED_STATUS);
    }

    /**
     * Whether the {@code status} the event recorded is a span status rather than its own outcome
     * code — the same reading {@link #EXCHANGE_CODE} makes, so the derivation cannot strip a key as
     * plumbing that it just judged an operation by.
     */
    static String recordedStatusIsSpanStatus() {
        return "%s IN (%s)".formatted(RECORDED_STATUS, SPAN_STATUS_VALUES);
    }
}
