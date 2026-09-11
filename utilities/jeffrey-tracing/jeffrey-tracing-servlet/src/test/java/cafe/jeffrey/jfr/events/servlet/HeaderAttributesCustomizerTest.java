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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The built-in header customizer, held to the shape the attribute index accepts: one scalar per
 * header, under a key that is settled once rather than per request.
 */
class HeaderAttributesCustomizerTest {

    private static final int MAX_VALUE_LENGTH = 256;

    @Test
    @DisplayName("a configured header is recorded under its lower-cased name")
    void configuredHeaderIsRecorded() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("X-Tenant-Id")),
                Map.of("x-tenant-id", List.of("acme")));

        assertEquals("{\"x-tenant-id\":\"acme\"}", attributes.json());
    }

    @Test
    @DisplayName("the name is matched however the request spells it")
    void lookupIsCaseInsensitive() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("x-tenant-id")),
                Map.of("X-TENANT-ID", List.of("acme")));

        assertEquals("{\"x-tenant-id\":\"acme\"}", attributes.json());
    }

    @Test
    @DisplayName("a header sent twice is one comma-joined value, never a list")
    void repeatedHeaderIsJoined() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("x-forwarded-for")),
                Map.of("x-forwarded-for", List.of("10.0.0.1", "10.0.0.2")));

        assertEquals("{\"x-forwarded-for\":\"10.0.0.1,10.0.0.2\"}", attributes.json());
    }

    @Test
    @DisplayName("a header that was not sent records nothing")
    void absentHeaderRecordsNothing() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("x-tenant-id")),
                Map.of());

        assertTrue(attributes.isEmpty());
    }

    @Test
    @DisplayName("a header sent empty records nothing, because the index would drop it")
    void emptyHeaderRecordsNothing() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("x-tenant-id")),
                Map.of("x-tenant-id", List.of("")));

        assertTrue(attributes.isEmpty());
    }

    @Test
    @DisplayName("the limit bounds the joined value, not each part of it")
    void oversizedHeaderIsTruncated() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(List.of("x-blob")),
                Map.of("x-blob", List.of("x".repeat(200), "y".repeat(200))));

        assertTrue(attributes.json().contains("…"), attributes.json());
        assertEquals(MAX_VALUE_LENGTH + 1, valueOf(attributes.json()).length());
    }

    @Test
    @DisplayName("the same header named twice, or padded, is one key")
    void namesAreDeduplicated() {
        HttpExchangeAttributes attributes = capture(
                HttpExchangeAttributesCustomizer.requestHeaders(Arrays.asList("X-Tenant-Id", " x-tenant-id ", "", null)),
                Map.of("x-tenant-id", List.of("acme")));

        assertEquals("{\"x-tenant-id\":\"acme\"}", attributes.json());
    }

    @Test
    @DisplayName("the key does not change shape under a Turkish locale")
    void keyIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            HttpExchangeAttributes attributes = capture(
                    HttpExchangeAttributesCustomizer.requestHeaders(List.of("X-TENANT-ID")),
                    Map.of("x-tenant-id", List.of("acme")));

            assertEquals("{\"x-tenant-id\":\"acme\"}", attributes.json());
        } finally {
            Locale.setDefault(original);
        }
    }

    private static HttpExchangeAttributes capture(
            HttpExchangeAttributesCustomizer customizer, Map<String, List<String>> requestHeaders) {

        HttpExchangeAttributes attributes = new HttpExchangeAttributes();
        customizer.customize(attributes, request(requestHeaders), mock(HttpServletResponse.class));
        return attributes;
    }

    /**
     * The servlet spec makes header lookup case-insensitive, which a mock does not do on its own —
     * so the stub answers the way a container would, and a test can send {@code X-TENANT-ID} and ask
     * for {@code x-tenant-id}.
     */
    private static HttpServletRequest request(Map<String, List<String>> headers) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Answer<Object> lookup = invocation ->
                Collections.enumeration(valuesOf(headers, invocation.getArgument(0)));
        when(request.getHeaders(anyString())).thenAnswer(lookup);
        return request;
    }

    private static Collection<String> valuesOf(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

    private static String valueOf(String json) {
        int start = json.indexOf(':') + 2;
        return json.substring(start, json.length() - 2);
    }
}
