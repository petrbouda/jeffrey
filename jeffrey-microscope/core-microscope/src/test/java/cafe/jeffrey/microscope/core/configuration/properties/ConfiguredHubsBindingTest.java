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

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the declared properties actually reach {@link ConfiguredHubsProperties}.
 * <p>
 * Worth its own test because the binder appends the field name to the prefix, so the annotation has
 * to say {@code jeffrey.microscope} for keys spelled {@code jeffrey.microscope.hubs.<key>.<field>}.
 * Getting that one level wrong binds nothing and fails silently — the feature would simply never
 * register a hub, with no error anywhere.
 */
class ConfiguredHubsBindingTest {

    @Configuration
    @EnableConfigurationProperties(ConfiguredHubsProperties.class)
    static class BindingTestConfiguration {
    }

    private static ConfiguredHubsProperties bind(String... properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(properties).applyTo(context);
            context.register(BindingTestConfiguration.class);
            context.refresh();
            return context.getBean(ConfiguredHubsProperties.class);
        }
    }

    @Test
    @DisplayName("Properties-file spelling binds every field")
    void bindsFromPropertyKeys() {
        ConfiguredHubsProperties properties = bind(
                "jeffrey.microscope.hubs.production.name=Production",
                "jeffrey.microscope.hubs.production.hostname=hub.example.com",
                "jeffrey.microscope.hubs.production.port=443",
                "jeffrey.microscope.hubs.staging.hostname=staging.internal",
                "jeffrey.microscope.hubs.staging.plaintext=true");

        List<DesiredHub> resolved = properties.resolve();
        assertEquals(2, resolved.size());

        DesiredHub production = resolved.stream()
                .filter(hub -> hub.key().equals("production"))
                .findFirst()
                .orElseThrow();
        assertEquals("cfg-production", production.hubId());
        assertEquals("Production", production.name());
        assertEquals("hub.example.com", production.address().hostname());
        assertEquals(443, production.address().port());

        DesiredHub staging = resolved.stream()
                .filter(hub -> hub.key().equals("staging"))
                .findFirst()
                .orElseThrow();
        // Unset name falls back to the key, unset port to the gRPC default.
        assertEquals("staging", staging.name());
        assertEquals(9090, staging.address().port());
        assertTrue(staging.address().plaintext());
    }

    @Test
    @DisplayName("Environment-variable spelling binds to the same hub")
    void bindsFromEnvironmentVariables() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource(
                            // Spring applies the underscore-to-dot relaxed mapping only to a
                            // SystemEnvironmentPropertySource whose name is "systemEnvironment" or
                            // ends with "-systemEnvironment"; any other name silently binds nothing.
                            "test-systemEnvironment",
                            Map.of(
                                    "JEFFREY_MICROSCOPE_HUBS_PRODUCTION_HOSTNAME", "hub.example.com",
                                    "JEFFREY_MICROSCOPE_HUBS_PRODUCTION_PORT", "443")));

            context.register(BindingTestConfiguration.class);
            context.refresh();

            List<DesiredHub> resolved =
                    context.getBean(ConfiguredHubsProperties.class).resolve();

            assertEquals(1, resolved.size());
            assertEquals("cfg-production", resolved.getFirst().hubId());
            assertEquals("hub.example.com", resolved.getFirst().address().hostname());
            assertEquals(443, resolved.getFirst().address().port());
        }
    }

    @Test
    @DisplayName("Declaring no hubs binds an empty map rather than failing")
    void bindsNothingWhenNoHubsAreDeclared() {
        assertTrue(bind("jeffrey.microscope.home.dir=/tmp/jeffrey").resolve().isEmpty());
    }
}
