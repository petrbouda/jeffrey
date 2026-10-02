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
package cafe.jeffrey.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSettingsTest {

    private static AgentSettings resolve(Map<String, String> properties, Map<String, String> environment) {
        return AgentSettings.resolve(properties::get, environment::get);
    }

    @Nested
    class Directory {

        @Test
        void readsTheSystemPropertyTheProvisionerWrites() {
            AgentSettings settings = resolve(Map.of(AgentSettings.DIRECTORY_PROPERTY, "/session/.heartbeat"), Map.of());

            assertEquals(Path.of("/session/.heartbeat"), settings.directory());
        }

        @Test
        void prefersThePropertyOverTheEnvironment() {
            AgentSettings settings = resolve(
                    Map.of(AgentSettings.DIRECTORY_PROPERTY, "/from/property"),
                    Map.of(AgentSettings.DIRECTORY_ENV, "/from/env"));

            assertEquals(Path.of("/from/property"), settings.directory());
        }

        @Test
        void fallsBackToTheEnvironment() {
            AgentSettings settings = resolve(Map.of(), Map.of(AgentSettings.DIRECTORY_ENV, "/from/env"));

            assertEquals(Path.of("/from/env"), settings.directory());
        }

        @Test
        void derivesItFromTheSessionDirectory() {
            AgentSettings settings = resolve(Map.of(), Map.of(AgentSettings.SESSION_ENV, "/session"));

            assertEquals(Path.of("/session/.heartbeat"), settings.directory());
        }

        @Test
        void isNullWhenNothingNamesOne() {
            assertNull(resolve(Map.of(), Map.of()).directory());
        }
    }

    @Nested
    class Enabled {

        @Test
        void isOnUnlessTheApplicationSaysOtherwise() {
            assertTrue(resolve(Map.of(), Map.of()).enabled());
        }

        @Test
        void honoursTheApplicationsSwitch() {
            assertFalse(resolve(Map.of(AgentSettings.ENABLED_PROPERTY, "false"), Map.of()).enabled());
            assertFalse(resolve(Map.of(), Map.of(AgentSettings.ENABLED_ENV, "false")).enabled());
        }
    }

    @Nested
    class Interval {

        @Test
        void defaultsToFiveSeconds() {
            assertEquals(Duration.ofSeconds(5), resolve(Map.of(), Map.of()).interval());
        }

        @Test
        void readsMilliseconds() {
            AgentSettings settings = resolve(Map.of(AgentSettings.INTERVAL_PROPERTY, "250"), Map.of());

            assertEquals(Duration.ofMillis(250), settings.interval());
        }

        @Test
        void fallsBackToTheDefaultOnAMalformedValue() {
            assertEquals(AgentSettings.DEFAULT_INTERVAL,
                    resolve(Map.of(AgentSettings.INTERVAL_PROPERTY, "soon"), Map.of()).interval());
            assertEquals(AgentSettings.DEFAULT_INTERVAL,
                    resolve(Map.of(AgentSettings.INTERVAL_PROPERTY, "-1"), Map.of()).interval());
        }

        @Test
        void rejectsANonPositiveIntervalGivenDirectly() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentSettings(Path.of("/x"), Duration.ZERO, true));
        }
    }
}
