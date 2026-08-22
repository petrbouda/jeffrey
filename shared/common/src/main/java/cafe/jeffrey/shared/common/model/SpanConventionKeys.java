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
 * The names a declared span naming convention travels under, from the {@code @Span} annotation
 * in a recording's metadata to the {@code event_types.extras} column the derivation reads.
 * <p>
 * Spelled here rather than referenced from {@code jeffrey-tracing} because neither the parser nor
 * the profile persistence may compile against the instrumentation library — the convention crosses
 * between them as data, inside the recording. {@code JdbcTraceRepositoryTest.EventApiContract}
 * pins every constant to the real annotation, so a rename on either side fails a test instead of
 * silently splitting the two.
 * <p>
 * Only <em>naming</em> is declarable. A span's verdict is the writer's statement — an exchange
 * that threw and still answered 200 knows something its code does not — so it is recorded through
 * {@code commitSpan()}/{@code failed()}, never derived from a declaration.
 *
 * <h2>Wire format</h2>
 * These values are read out of recordings and profiles that outlive every version of Jeffrey. They
 * are frozen: never renamed, only added to.
 */
public abstract class SpanConventionKeys {

    /** Fully qualified name of the {@code @Span} annotation, as JFR metadata spells it. */
    public static final String SPAN_ANNOTATION = "cafe.jeffrey.jfr.events.trace.Span";

    /** Extras key holding the {@code @Span} template, e.g. {@code "{method} {uri}"}. */
    public static final String EXTRAS_SPAN_NAME = "spanName";
}
