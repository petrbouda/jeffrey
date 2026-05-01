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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import cafe.jeffrey.shared.common.Json;

import java.io.IOException;

/**
 * Emits a JFR {@link HttpServerExchangeEvent} for each HTTP exchange. The
 * matched URI template (e.g. {@code /api/internal/profiles/{profileId}}) is
 * read from the request attributes populated by Spring's
 * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}; falls back to the
 * raw URI when no template was matched.
 */
public class JeffreyJfrHttpEventFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        event.begin();
        try {
            filterChain.doFilter(request, response);
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = request.getRemoteHost();
                event.remotePort = request.getRemotePort();
                event.uri = resolveTemplateUri(request);
                event.method = request.getMethod();
                event.mediaType = request.getContentType();
                event.queryParams = Json.toString(splitQueryParameters(request));
                event.pathParams = Json.toString(extractPathParameters(request));
                event.requestLength = parseLong(request.getHeader("Content-Length"));
                event.responseLength = parseLong(response.getHeader("Content-Length"));
                event.status = response.getStatus();
                event.commit();
            }
        }
    }

    private static String resolveTemplateUri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isEmpty()) {
            String contextPath = request.getContextPath();
            return (contextPath == null || contextPath.isEmpty()) ? s : contextPath + s;
        }
        return request.getRequestURI();
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> extractPathParameters(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof java.util.Map<?, ?> map) {
            return (java.util.Map<String, String>) map;
        }
        return java.util.Map.of();
    }

    private static java.util.Map<String, java.util.List<String>> splitQueryParameters(HttpServletRequest request) {
        java.util.Map<String, java.util.List<String>> result = new java.util.LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> result.put(k, java.util.List.of(v)));
        return result;
    }

    private static long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
