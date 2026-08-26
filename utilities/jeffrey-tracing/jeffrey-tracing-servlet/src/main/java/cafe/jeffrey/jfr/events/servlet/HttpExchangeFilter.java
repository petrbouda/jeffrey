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

package cafe.jeffrey.jfr.events.servlet;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Emits one {@link HttpServerExchangeEvent} per inbound request, opened as the <b>root span</b> of
 * that request's trace.
 * <p>
 * The exchange event <em>is</em> the root span: {@link Tracer#inSpanOf} stamps it with a fresh
 * trace and span id and publishes that context for the duration of the chain, so every statement,
 * outbound call and hand-written {@code Tracer} span recorded while serving the request nests
 * underneath it. No separate span event is emitted, because one would record the same interval
 * twice.
 * <p>
 * Depends on {@code jakarta.servlet} and nothing else. What the request is <em>called</em> is the
 * one thing a container cannot answer, so it is asked of a {@link HttpRequestNaming} — see that
 * interface for why the name must be a route template rather than the raw path.
 *
 * <h2>Registration</h2>
 * Register it first in the chain, so security, routing and data access all happen inside the span.
 * On Spring Boot, {@code jeffrey-tracing-spring-boot-starter} does this for you.
 *
 * <h2>Asynchronous requests</h2>
 * When the handler starts async processing, the response is not finished when the container thread
 * returns — completing the event there would record a request that appears to take microseconds.
 * The filter instead completes it from an {@link AsyncListener}, so the recorded interval covers
 * the whole exchange. The ids were already stamped when the span opened, so the deferred commit
 * still lands in the right trace; JFR attributes the event to the thread that commits it, which for
 * an async request is the thread that completed it.
 */
public class HttpExchangeFilter implements Filter {

    /** Guards against a second application when the filter is mapped more than once. */
    private static final String APPLIED_ATTRIBUTE = HttpExchangeFilter.class.getName() + ".APPLIED";

    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final long UNKNOWN_LENGTH = -1;

    private final HttpRequestNaming naming;
    private final HttpExchangeSettings settings;

    public HttpExchangeFilter(HttpRequestNaming naming, HttpExchangeSettings settings) {
        this.naming = Objects.requireNonNull(naming, "naming must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * The form that names requests by their servlet mapping and records no parameters.
     */
    public HttpExchangeFilter() {
        this(HttpRequestNaming.servletMapping(), HttpExchangeSettings.defaults());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || httpRequest.getAttribute(APPLIED_ATTRIBUTE) != null) {

            chain.doFilter(request, response);
            return;
        }
        httpRequest.setAttribute(APPLIED_ATTRIBUTE, Boolean.TRUE);

        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        event.begin();
        try {
            // Opens the root span of the request's trace and binds it for the whole chain.
            inSpan(event, chain, request, response);
        } finally {
            if (httpRequest.isAsyncStarted()) {
                httpRequest.getAsyncContext().addListener(new CompletionListener(event, httpRequest, httpResponse));
            } else {
                complete(event, httpRequest, httpResponse);
            }
        }
    }

    private void inSpan(
            HttpServerExchangeEvent event, FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {

        try {
            Tracer.inSpanOf(event, () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException | Error e) {
            // Tracer infers one thrown type, which widens to Exception for a body throwing both
            // IOException and ServletException. Narrow it back to what a filter may declare.
            throw e;
        } catch (Throwable t) {
            throw new ServletException(t);
        }
    }

    /**
     * Ends the interval and commits, filling the event's fields only when it is actually going to
     * be recorded.
     */
    private void complete(HttpServerExchangeEvent event, HttpServletRequest request, HttpServletResponse response) {
        event.end();
        if (!event.shouldCommit()) {
            return;
        }

        event.remoteHost = request.getRemoteHost();
        event.remotePort = request.getRemotePort();
        event.uri = naming.uri(request);
        event.method = request.getMethod();
        event.mediaType = request.getContentType();
        event.statusCode = response.getStatus();
        event.requestLength = contentLength(request.getHeader(CONTENT_LENGTH_HEADER));
        event.responseLength = contentLength(response.getHeader(CONTENT_LENGTH_HEADER));

        if (settings.captureQueryParams()) {
            event.queryParams = asJson(queryParams(request));
        }
        if (settings.capturePathParams()) {
            event.pathParams = asJson(naming.pathParams(request));
        }
        // commitSpan(), which here commits under the identity inSpanOf already stamped: it never
        // re-stamps, so the children recorded under the original span id keep their parent.
        event.commitSpan();
    }

    private static Map<String, String> queryParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", List.of(entry.getValue())),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private static String asJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        EventAttributes attributes = EventAttributes.create();
        values.forEach(attributes::put);
        return attributes.json();
    }

    private static long contentLength(String header) {
        if (header == null || header.isEmpty()) {
            return UNKNOWN_LENGTH;
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            return UNKNOWN_LENGTH;
        }
    }

    /**
     * Completes the event once the asynchronous exchange really finishes.
     * <p>
     * A timeout or a handler failure is recorded on the span before completion: the container still
     * fires {@code onComplete} afterwards, and a span that ended in an error should say so rather
     * than be judged only by whatever status code was finally written.
     */
    private final class CompletionListener implements AsyncListener {

        private final HttpServerExchangeEvent event;
        private final HttpServletRequest request;
        private final HttpServletResponse response;

        private CompletionListener(
                HttpServerExchangeEvent event, HttpServletRequest request, HttpServletResponse response) {
            this.event = event;
            this.request = request;
            this.response = response;
        }

        @Override
        public void onComplete(AsyncEvent asyncEvent) {
            complete(event, request, response);
        }

        @Override
        public void onTimeout(AsyncEvent asyncEvent) {
            recordFailure(asyncEvent);
        }

        @Override
        public void onError(AsyncEvent asyncEvent) {
            recordFailure(asyncEvent);
        }

        @Override
        public void onStartAsync(AsyncEvent asyncEvent) {
            // A re-dispatched async request keeps the same event; the listener has to be
            // re-registered because the container clears listeners on each async cycle.
            asyncEvent.getAsyncContext().addListener(this);
        }

        private void recordFailure(AsyncEvent asyncEvent) {
            if (asyncEvent.getThrowable() != null) {
                event.failed(asyncEvent.getThrowable());
            }
        }
    }
}
