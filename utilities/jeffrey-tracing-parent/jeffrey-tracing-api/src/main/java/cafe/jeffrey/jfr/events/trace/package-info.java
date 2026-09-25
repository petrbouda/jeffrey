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

/**
 * The tracing core: what makes any Jeffrey event part of a trace, and the API for recording spans
 * of your own.
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent} — the span shape every traced
 *       event carries: ids, name, kind, status, error type, attributes, and the single commit verb
 *       {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()}</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.Tracer} — records hand-written spans
 *       ({@code run}/{@code call}), opens an event as its own span ({@code inSpanOf} /
 *       {@code openSpanOf}), and carries traces across threads ({@code fork}, {@code continueIn},
 *       {@code reenter})</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.TracedEvents} — the leaf emit shape written once:
 *       guard, begin, the work, failure recording, fill, commit</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.TraceSpanEvent} ({@code jeffrey.TraceSpan}) — the
 *       event {@code Tracer} emits for an interval no other instrumentation describes</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.TraceScopeEvent} ({@code jeffrey.TraceScope}) — one
 *       stretch of a re-entered span's life on one thread; emitted only by
 *       {@link cafe.jeffrey.jfr.events.trace.Tracer#reenter Tracer.reenter}</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.SpanContext} — a span's position in its trace,
 *       immutable and safe to carry across threads</li>
 *   <li>{@link cafe.jeffrey.jfr.events.trace.Span} — the metadata annotation that writes an event
 *       type's naming template into every recording it appears in</li>
 * </ul>
 * Start with {@link cafe.jeffrey.jfr.events.trace.Tracer} — its class documentation covers the
 * model, the cost when nothing is recording, and the limits.
 *
 * <p>Java 21+. Where the span in progress is kept is not part of this package: add
 * {@code jeffrey-tracing-scoped-value} ({@code ScopedValue}, Java 25+) or
 * {@code jeffrey-tracing-thread-local} ({@code ThreadLocal}, Java 21+) - see
 * {@link cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage}.
 */
package cafe.jeffrey.jfr.events.trace;
