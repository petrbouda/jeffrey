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

package cafe.jeffrey.heartbeat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatSettingsTest {

    private static HeartbeatSettings resolve(Map<String, String> properties, Map<String, String> environment) {
        return HeartbeatSettings.resolve(properties::get, environment::get);
    }

    @Nested
    class Directory {

        @Test
        void comesFromTheHeartbeatVariableWhenSet() {
            HeartbeatSettings settings = resolve(Map.of(), Map.of(
                    HeartbeatSettings.DIRECTORY_ENV, "/sessions/s-1/.heartbeat"));

            assertEquals(Path.of("/sessions/s-1/.heartbeat"), settings.directory());
        }

        @Test
        void fallsBackToTheSessionDirectory() {
            // A session provisioned before JEFFREY_HEARTBEAT_DIR existed still resolves, because
            // the heartbeat folder has always sat inside the session directory
            HeartbeatSettings settings = resolve(Map.of(), Map.of(
                    HeartbeatSettings.SESSION_ENV, "/sessions/s-1"));

            assertEquals(Path.of("/sessions/s-1/.heartbeat"), settings.directory());
        }

        @Test
        void prefersTheExplicitVariableOverTheSessionDirectory() {
            HeartbeatSettings settings = resolve(Map.of(), Map.of(
                    HeartbeatSettings.DIRECTORY_ENV, "/elsewhere",
                    HeartbeatSettings.SESSION_ENV, "/sessions/s-1"));

            assertEquals(Path.of("/elsewhere"), settings.directory());
        }

        @Test
        void isNullWhenNothingSaid() {
            HeartbeatSettings settings = resolve(Map.of(), Map.of());

            assertNull(settings.directory());
            assertFalse(settings.writable(), "nowhere to write means nothing is written");
        }

        @Test
        void aSystemPropertyOverridesTheEnvironment() {
            HeartbeatSettings settings = resolve(
                    Map.of("jeffrey.heartbeat.dir", "/from-property"),
                    Map.of(HeartbeatSettings.DIRECTORY_ENV, "/from-environment"));

            assertEquals(Path.of("/from-property"), settings.directory());
        }
    }

    @Nested
    class Enabled {

        @Test
        void defaultsToOn() {
            assertTrue(resolve(Map.of(), Map.of()).enabled());
        }

        @Test
        void isOffWhenTheProvisionerSaysTheAgentIsBeating() {
            HeartbeatSettings settings = resolve(Map.of(), Map.of(
                    HeartbeatSettings.ENABLED_ENV, "false",
                    HeartbeatSettings.DIRECTORY_ENV, "/sessions/s-1/.heartbeat"));

            assertFalse(settings.enabled());
            assertFalse(settings.writable(), "a directory is not permission to write to it");
        }
    }

    @Nested
    class Interval {

        @Test
        void defaultsToTheSharedConstant() {
            assertEquals(HeartbeatFiles.DEFAULT_INTERVAL, resolve(Map.of(), Map.of()).interval());
        }

        @Test
        void readsMilliseconds() {
            HeartbeatSettings settings = resolve(Map.of(), Map.of(HeartbeatSettings.INTERVAL_ENV, "2500"));

            assertEquals(Duration.ofMillis(2500), settings.interval());
        }

        @Test
        void fallsBackToTheDefaultWhenUnparseable() {
            // Never throws: failing an application's startup over a malformed liveness setting
            // would be a worse outcome than not reporting liveness
            assertEquals(HeartbeatFiles.DEFAULT_INTERVAL,
                    resolve(Map.of(), Map.of(HeartbeatSettings.INTERVAL_ENV, "every 5 seconds")).interval());
            assertEquals(HeartbeatFiles.DEFAULT_INTERVAL,
                    resolve(Map.of(), Map.of(HeartbeatSettings.INTERVAL_ENV, "-1")).interval());
        }

        @Test
        void rejectsANonPositiveIntervalGivenDirectly() {
            assertThrows(IllegalArgumentException.class,
                    () -> new HeartbeatSettings(Path.of("/tmp"), Duration.ZERO, true));
        }
    }
}
