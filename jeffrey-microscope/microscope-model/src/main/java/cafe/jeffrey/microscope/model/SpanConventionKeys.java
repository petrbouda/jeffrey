/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.model;

/**
 * The names a declared span naming convention travels under, from the {@code @Span} annotation
 * in a recording's metadata to the {@code event_types.extras} column the derivation reads.
 * <p>
 * Spelled here rather than referenced from {@code jeffrey-events} because neither the parser nor
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
