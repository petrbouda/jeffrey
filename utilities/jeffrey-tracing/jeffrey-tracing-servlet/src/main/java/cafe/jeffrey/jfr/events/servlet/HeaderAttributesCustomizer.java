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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The built-in answer to {@link HttpExchangeAttributesCustomizer}: records an allow-list of headers.
 * <p>
 * A class rather than a lambda, unlike {@link HttpRequestNaming#servletMapping()}, because it
 * carries the normalized allow-lists; it is reachable only through the interface's factories.
 */
final class HeaderAttributesCustomizer implements HttpExchangeAttributesCustomizer {

    private static final Logger LOG = System.getLogger(HeaderAttributesCustomizer.class.getName());

    /**
     * The key namespace, which is OpenTelemetry's, so a reader meeting
     * {@code http.request.header.x-tenant-id} in a key list needs no legend. Dotted keys are safe:
     * Jeffrey's attribute index addresses a key by a quoted JSON path precisely so that keys with
     * dots survive.
     */
    private static final String REQUEST_HEADER_PREFIX = "http.request.header.";
    private static final String RESPONSE_HEADER_PREFIX = "http.response.header.";

    /**
     * Not the array form OpenTelemetry uses for a repeated header: the attribute index drops any
     * value that is an array, so a list would be recorded and then never found. One joined scalar,
     * the same way {@link HttpExchangeFilter} joins repeated query parameters.
     */
    private static final String HEADER_VALUE_SEPARATOR = ",";

    /**
     * Headers whose value is a credential. Naming one is not refused — silently ignoring something
     * a developer configured on purpose is its own kind of surprise — but it is said out loud once,
     * at startup, because a recording is a file that leaves the building.
     */
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");

    private final List<String> requestNames;
    private final List<String> responseNames;

    HeaderAttributesCustomizer(Collection<String> requestNames, Collection<String> responseNames) {
        this.requestNames = normalize(requestNames);
        this.responseNames = normalize(responseNames);
    }

    @Override
    public void customize(
            HttpExchangeAttributes attributes, HttpServletRequest request, HttpServletResponse response) {

        for (String name : requestNames) {
            put(attributes, REQUEST_HEADER_PREFIX + name, requestValues(request, name));
        }
        for (String name : responseNames) {
            Collection<String> values = response.getHeaders(name);
            put(attributes, RESPONSE_HEADER_PREFIX + name, values == null ? List.of() : values);
        }
    }

    private static void put(HttpExchangeAttributes attributes, String key, Collection<String> values) {
        if (values.isEmpty()) {
            return;
        }

        StringJoiner joined = new StringJoiner(HEADER_VALUE_SEPARATOR);
        for (String value : values) {
            joined.add(value);
        }
        // Truncation happens inside put(), i.e. after joining, so the limit bounds what is recorded
        // rather than each part of it.
        attributes.put(key, joined.toString());
    }

    /**
     * The request half answers with an {@link Enumeration} where the response half answers with a
     * {@link Collection}, so one of the two has to be adapted.
     */
    private static Collection<String> requestValues(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null) {
            return List.of();
        }
        return Collections.list(values);
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

        for (String name : normalized) {
            if (SENSITIVE_HEADERS.contains(name)) {
                LOG.log(Level.WARNING,
                        "Recording a credential-bearing HTTP header into JFR, which is a file that gets"
                                + " shared and kept: header={0}", name);
            }
        }
        return List.copyOf(normalized);
    }
}
