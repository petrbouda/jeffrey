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
 * HTTP exchange events: one event per request/response pair, on whichever side of the wire this
 * process stands.
 * <p>
 * Both events share the {@link cafe.jeffrey.jfr.events.http.AbstractHttpExchangeEvent} shape and
 * derive their span the same way — named {@code "{method} {uri}"}, failed at status {@code 400}
 * and above — but they play opposite roles in a trace:
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent} ({@code
 *       jeffrey.HttpServerExchange}) describes an <em>inbound</em> request and is normally the
 *       <b>root of the request's trace</b>: nothing in this process encloses it.</li>
 *   <li>{@link cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent} ({@code
 *       jeffrey.HttpClientExchange}) describes an <em>outbound</em> call and is a <b>leaf</b>: the
 *       downstream work happens in a process this recording cannot see.</li>
 * </ul>
 *
 * <h2>Emitting the server exchange (the trace root)</h2>
 * The natural integration point is a servlet filter registered first in the chain, so security,
 * routing and data access all happen inside the span. The event <em>is</em> the root span:
 * {@link cafe.jeffrey.jfr.events.trace.Tracer#inSpanOf Tracer.inSpanOf} stamps it with fresh ids
 * and binds the context for the duration of the request — no separate span event is emitted for
 * the same interval.
 *
 * <pre>{@code
 * HttpServerExchangeEvent event = new HttpServerExchangeEvent();
 * if (!event.isEnabled()) {
 *     chain.doFilter(request, response);
 *     return;
 * }
 * event.begin();
 * try {
 *     Tracer.inSpanOf(event, () -> {
 *         chain.doFilter(request, response);
 *         return null;
 *     });
 * } finally {
 *     event.end();
 *     if (event.shouldCommit()) {
 *         event.method = request.getMethod();
 *         event.uri = matchedTemplate(request);        // "/api/users/{id}", never the raw path
 *         event.statusCode = response.getStatus();
 *         event.commitSpan();                          // already stamped by inSpanOf
 *     }
 * }
 * }</pre>
 *
 * <h2>Emitting the client exchange (a leaf)</h2>
 * A client interceptor commits in its own {@code finally};
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()} nests the
 * event under whatever span is in progress — usually the server exchange of the request being
 * served — or records it untraced when there is none. A transport failure that never produced a
 * status code is recorded with
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#failed(Throwable) failed(Throwable)}.
 *
 * <pre>{@code
 * HttpClientExchangeEvent event = new HttpClientExchangeEvent();
 * if (!event.isEnabled()) {
 *     return execution.execute(request, body);
 * }
 * event.begin();
 * try {
 *     ClientHttpResponse response = execution.execute(request, body);
 *     event.end();
 *     event.statusCode = response.getStatusCode().value();
 *     return response;
 * } catch (IOException e) {
 *     event.failed(e);                                 // no status code ever arrived
 *     throw e;
 * } finally {
 *     if (event.shouldCommit()) {
 *         event.method = request.getMethod().name();
 *         event.uri = request.getURI().getHost() + normalizedPath(request);
 *         event.commitSpan();                          // stamps as a child of the span in progress
 *     }
 * }
 * }</pre>
 *
 * <h2>Cardinality</h2>
 * The {@code uri} field must be the matched template ({@code /api/users/{id}}) or a path with
 * variable segments collapsed — never a URL containing an entity id. Jeffrey's HTTP dashboards
 * aggregate per endpoint on it, and the span name is derived from it. Request identity already
 * lives in the trace id.
 */
package cafe.jeffrey.jfr.events.http;
