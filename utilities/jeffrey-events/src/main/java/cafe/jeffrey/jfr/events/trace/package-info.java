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
 * <p>Requires Java 25: the API is built on {@link java.lang.ScopedValue} and
 * {@code jdk.jfr.Contextual}, both finalized there.
 */
package cafe.jeffrey.jfr.events.trace;
