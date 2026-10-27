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
