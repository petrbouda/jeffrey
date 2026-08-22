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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the configuration that replaced this module's hand-written JFR HTTP filter.
 * <p>
 * The filter now ships in {@code jeffrey-events-spring-boot-starter} and is registered from
 * {@code jeffrey.tracing.*}. That is a better arrangement — one implementation instead of one per
 * adopter — but it moves the filter's placement out of Java, where the compiler was checking it,
 * and into a properties file, where nothing is. These assertions are that check: a changed value
 * here silently reorders Jeffrey's filter chain or stops it tracing its own API.
 */
class JeffreyTracingPropertiesTest {

    private static final String PROPERTIES = "/application.properties";

    /**
     * What the deleted filter registered itself as: just after {@code JeffreyRequestLoggingFilter},
     * which holds {@link Ordered#HIGHEST_PRECEDENCE}.
     */
    private static final int EXPECTED_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Test
    @DisplayName("the starter reproduces exactly what the hand-written filter did")
    void reproducesTheReplacedFilter() throws IOException {
        Properties properties = load();

        assertEquals("/api/*", properties.getProperty("jeffrey.tracing.url-patterns"),
                "the filter only ever traced the API, not static assets");
        assertEquals(EXPECTED_ORDER, Integer.parseInt(properties.getProperty("jeffrey.tracing.order")),
                "the JFR filter runs just after the request-logging filter, as it did before");
    }

    @Test
    @DisplayName("Jeffrey opts into parameter capture, which the library leaves off by default")
    void optsIntoParameterCapture() throws IOException {
        Properties properties = load();

        assertEquals("true", properties.getProperty("jeffrey.tracing.capture-query-params"));
        assertEquals("true", properties.getProperty("jeffrey.tracing.capture-path-params"));
    }

    private static Properties load() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = JeffreyTracingPropertiesTest.class.getResourceAsStream(PROPERTIES)) {
            properties.load(stream);
        }
        return properties;
    }
}
