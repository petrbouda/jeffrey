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
 * The promotion of {@code jdk.MethodTrace} (JEP 520) into spans, and how it differs from
 * {@link BlockingLeafSpans}.
 * <p>
 * Three differences, each of which the derivation has to honour:
 * <ol>
 *   <li><b>The name is the event's, not ours.</b> A blocking event promotes to a fixed label — every
 *       {@code jdk.SocketRead} is "Socket read". A traced method promotes to <em>itself</em>, so the
 *       name is read out of the event's own {@code method} field.</li>
 *   <li><b>It is not a leaf.</b> A traced method's duration includes the methods it calls, so traced
 *       methods nest inside one another and a blocking wait can happen inside one. A method span can
 *       therefore be the parent of another promoted span, which no other promotion can — and of a
 *       <em>recorded</em> span: a recorded span that begins inside a method span standing between it
 *       and its own recorded parent is re-hung under that method span (the derivation's adoption
 *       stage), so a traced controller method wrapping a request contains the request's recorded
 *       spans instead of sitting beside them as their sibling.</li>
 *   <li><b>It is work, not waiting.</b> It maps to no {@code TraceContextCategory} on purpose: the
 *       why-slow panel accounts for waits, and a traced method's time is the trace's own work. Giving
 *       it a category would move that time out of "own work" and into a wait total that never
 *       happened.</li>
 * </ol>
 *
 * <h2>Reading the method out of the event</h2>
 * The JFR {@code jdk.types.Method} struct is flattened by {@code EventFieldsToJsonMapper} to the
 * single string {@code "pkg.Class#method"}, so the traced method is
 * {@code json_extract_string(fields, '$.method')}.
 * <p>
 * It has to be that field and not {@code weight_entity}, which for most events is the same idea.
 * JEP 520 roots a {@code jdk.MethodTrace} stack trace at the <em>caller</em>, so the leaf frame that
 * fills {@code weight_entity} names the method that made the call rather than the one being traced.
 * Verified on a JDK 25 recording: an event with {@code method = Probe.inner()} carries
 * {@code Probe.outer()} as its leaf frame.
 */
final class MethodSpans {

    /** The only event type this promotion reads. */
    static final String EVENT_TYPE = EventTypeName.METHOD_TRACE;

    /**
     * Method spans are {@code INTERNAL}: a traced method is the application's own work, not a call
     * out to anything.
     */
    static final String KIND = "INTERNAL";

    /** The flattened {@code "pkg.Class#method"} field the span's name is built from. */
    static final String METHOD_FIELD = "method";

    /**
     * The character between the class and the method, in the name and in the field it is read from.
     * <p>
     * JFR's own separator, kept rather than swapped for a dot: it is what says "a method" the way
     * gRPC's {@code {service}/{method}} says "an RPC", and a dot would make {@code Outer.Inner.run}
     * ambiguous about where the type ends. Doubling as the delimiter the field is split on is the
     * point — one constant, so the name cannot be assembled with a separator the parse never saw.
     */
    static final String SEPARATOR = "#";

    /**
     * The last-resort name. {@code trace_spans.name} is {@code NOT NULL}, so a recording that writes
     * the method field in a shape this build cannot split still has to produce a span rather than
     * fail the whole profile's initialisation.
     */
    static final String UNNAMED = "Traced method";

    /**
     * The span name, as a SQL expression over a {@code fields} JSON column.
     * <p>
     * {@code "pkg.Class#method"} becomes {@code "Class#method"} — the package is dropped because a
     * waterfall row is a few centimetres wide and the qualified name pushes the timing off the end of
     * it. Nothing is lost: the full string stays in the span's payload, which the detail panel shows.
     * A value that does not split (a shape this build has not seen) falls back to itself rather than
     * to a null name, since {@code name} is {@code NOT NULL}.
     *
     * @param fieldsColumn the JSON expression to read {@link #METHOD_FIELD} out of. It must be one
     *                     the pooled field has already been spliced back into — a traced method's
     *                     qualified name is long enough that the parser pools it, so reading the raw
     *                     {@code events_raw.fields} finds no method at all and every span in a real
     *                     recording lands on {@link #UNNAMED}.
     */
    static String nameExpression(String fieldsColumn) {
        String raw = "json_extract_string(" + fieldsColumn + ", '$." + METHOD_FIELD + "')";
        String simpleClass = "regexp_extract(split_part(" + raw + ", '" + SEPARATOR + "', 1), '([^.]+)$', 1)";
        String methodName = "split_part(" + raw + ", '" + SEPARATOR + "', 2)";
        return "COALESCE(NULLIF(NULLIF(" + simpleClass + ", '') || '" + SEPARATOR + "' || NULLIF(" + methodName
                + ", ''), '" + SEPARATOR + "'), " + raw + ", '" + UNNAMED + "')";
    }

    /**
     * The {@code name} and {@code kind} columns of the methods CTE, as the derivation splices them
     * in.
     * <p>
     * Reads a plain {@code fields} column rather than repeating the rehydration three times, which
     * is only safe because the derivation selects it from a CTE that already carries one. It used to
     * be spliced into a SELECT over {@code events_raw} instead, where an unqualified {@code fields}
     * resolves to that table's own column — DuckDB gives a base column precedence over a lateral
     * alias of the same name — so the rehydrated alias one line above was silently shadowed by the
     * raw JSON the pooled method name had been lifted out of.
     */
    static String nameAndKindProjection() {
        return nameExpression("fields") + " AS name,\n                    '" + KIND + "' AS kind,";
    }

    private MethodSpans() {
    }
}
