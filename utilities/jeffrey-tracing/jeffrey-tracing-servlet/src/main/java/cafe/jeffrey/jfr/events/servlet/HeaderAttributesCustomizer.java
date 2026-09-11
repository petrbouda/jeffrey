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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The built-in answer to {@link HttpExchangeAttributesCustomizer}: records an allow-list of headers.
 * <p>
 * The attribute key <em>is</em> the header name, as the allow-list normalized it — trimmed and
 * lower-cased — so {@code x-tenant-id} is what is configured, what the client sends and what a
 * search asks for, with nothing in between to remember.
 * <p>
 * Request headers only. A response header is something the application's own server already set, so
 * it needs no allow-list to be reachable — a customizer of two lines reads it off the response it is
 * handed. An inbound header is the case nothing else covers: it arrives from outside, and the
 * application may never touch it.
 * <p>
 * A header sent more than once records its <em>first</em> value and nothing else. An attribute is
 * one scalar — the index drops arrays, and a joined string is a value nobody can search for by
 * either half — so of the three ways to answer "which one", the first is the one the servlet API
 * already calls the header's value.
 * <p>
 * A class rather than a lambda, unlike {@link HttpRequestNaming#servletMapping()}, because it
 * carries the normalized allow-list; it is reachable only through the interface's factory.
 */
final class HeaderAttributesCustomizer implements HttpExchangeAttributesCustomizer {

    private final List<String> names;

    HeaderAttributesCustomizer(Collection<String> names) {
        this.names = normalize(names);
    }

    @Override
    public void customize(
            HttpExchangeAttributes attributes, HttpServletRequest request, HttpServletResponse response) {

        for (String name : names) {
            // getHeader is the servlet API's own "first value"; an absent header answers null,
            // which the sink skips.
            attributes.put(name, request.getHeader(name));
        }
    }

    /**
     * Settles the header names once, so nothing about them is decided per request.
     * <p>
     * Lower-cased with {@link Locale#ROOT} deliberately: under a Turkish default locale
     * {@code "X-TENANT-ID".toLowerCase()} yields a dotless {@code ı}, and the recorded key would
     * quietly change shape depending on where the application runs.
     */
    private static List<String> normalize(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            normalized.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }
}
