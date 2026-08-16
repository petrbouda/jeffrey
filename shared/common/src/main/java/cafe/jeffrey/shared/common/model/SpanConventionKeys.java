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

package cafe.jeffrey.shared.common.model;

/**
 * The names a declared span convention travels under, from the annotation in a recording's
 * metadata to the {@code event_types.extras} column the derivation reads.
 * <p>
 * Spelled here rather than referenced from {@code jeffrey-events} because neither the parser nor
 * the profile persistence may compile against the instrumentation library — the convention crosses
 * between them as data, inside the recording. {@code JdbcTraceRepositoryTest.EventApiContract}
 * pins every constant to the real annotation, so a rename on either side fails a test instead of
 * silently splitting the two.
 *
 * <h2>Wire format</h2>
 * All of these values — the annotation type names, the extras keys, and the semantics names — are
 * read out of recordings and profiles that outlive every version of Jeffrey. They are frozen:
 * never renamed, only added to. A reader finding a semantics value it does not know must skip the
 * declaration, not fail.
 */
public abstract class SpanConventionKeys {

    /** Fully qualified name of the {@code @SpanName} annotation, as JFR metadata spells it. */
    public static final String SPAN_NAME_ANNOTATION = "cafe.jeffrey.jfr.events.trace.SpanName";

    /** Fully qualified name of the {@code @SpanOutcome} annotation. */
    public static final String SPAN_OUTCOME_ANNOTATION = "cafe.jeffrey.jfr.events.trace.SpanOutcome";

    /** Extras key holding the {@code @SpanName} template, e.g. {@code "{method} {uri}"}. */
    public static final String EXTRAS_SPAN_NAME = "spanName";

    /** Extras key holding {@code @SpanOutcome.from()} — the field carrying the outcome code. */
    public static final String EXTRAS_OUTCOME_FROM = "spanOutcomeFrom";

    /** Extras key holding {@code @SpanOutcome.semantics()} — one of the semantics names below. */
    public static final String EXTRAS_OUTCOME_SEMANTICS = "spanOutcomeSemantics";

    /** A numeric code judged the HTTP way: {@code >= 400} is ERROR, below is UNSET. */
    public static final String SEMANTICS_HTTP_CODE = "HTTP_CODE";

    /** A textual code judged the gRPC way: {@code OK} is OK, anything else is ERROR. */
    public static final String SEMANTICS_GRPC_CODE = "GRPC_CODE";

    /**
     * A success flag: {@code false} is ERROR, anything else is UNSET. Read, but not currently
     * written: no shipped annotation constant mints it — {@code SpanOutcome} publishes only the
     * semantics its own event types prove out. It is understood here regardless, both as the
     * built-in arm for the {@code isSuccess} flag older statements recorded and as forward
     * compatibility: a future library version that publishes it produces recordings this version
     * already reads.
     */
    public static final String SEMANTICS_BOOLEAN = "BOOLEAN";
}
