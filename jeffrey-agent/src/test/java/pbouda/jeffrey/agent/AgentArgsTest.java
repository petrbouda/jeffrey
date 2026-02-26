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

package pbouda.jeffrey.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AgentArgsTest {

    @Nested
    class NullOrBlankArgs {

        @Test
        void nullArgsReturnsDefaults() {
            AgentArgs args = AgentArgs.parse(null);

            assertNull(args.heartbeatDir());
            assertEquals(Duration.ofSeconds(5), args.heartbeatInterval());
            assertTrue(args.heartbeatEnabled());
        }

        @Test
        void emptyStringReturnsDefaults() {
            AgentArgs args = AgentArgs.parse("");

            assertNull(args.heartbeatDir());
            assertEquals(Duration.ofSeconds(5), args.heartbeatInterval());
            assertTrue(args.heartbeatEnabled());
        }

        @Test
        void blankStringReturnsDefaults() {
            AgentArgs args = AgentArgs.parse("   ");

            assertNull(args.heartbeatDir());
            assertEquals(Duration.ofSeconds(5), args.heartbeatInterval());
            assertTrue(args.heartbeatEnabled());
        }
    }

    @Nested
    class HeartbeatDir {

        @Test
        void parsesHeartbeatDir() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp/session/.heartbeat");

            assertEquals(Path.of("/tmp/session/.heartbeat"), args.heartbeatDir());
            assertTrue(args.heartbeatEnabled());
        }

        @Test
        void missingHeartbeatDirReturnsNull() {
            AgentArgs args = AgentArgs.parse("heartbeat.interval=5000");

            assertNull(args.heartbeatDir());
        }
    }

    @Nested
    class HeartbeatInterval {

        @Test
        void parsesCustomInterval() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp,heartbeat.interval=5000");

            assertEquals(Duration.ofMillis(5000), args.heartbeatInterval());
        }

        @Test
        void defaultIntervalWhenNotSpecified() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp");

            assertEquals(Duration.ofSeconds(5), args.heartbeatInterval());
        }
    }

    @Nested
    class HeartbeatEnabled {

        @Test
        void disabledExplicitly() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp,heartbeat.enabled=false");

            assertFalse(args.heartbeatEnabled());
            assertEquals(Path.of("/tmp"), args.heartbeatDir());
        }

        @Test
        void enabledByDefault() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp");

            assertTrue(args.heartbeatEnabled());
        }

        @Test
        void enabledExplicitly() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp,heartbeat.enabled=true");

            assertTrue(args.heartbeatEnabled());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void unknownKeysAreIgnored() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp,unknown.key=value");

            assertEquals(Path.of("/tmp"), args.heartbeatDir());
        }

        @Test
        void spacesAroundPairsAreTrimmed() {
            AgentArgs args = AgentArgs.parse(" heartbeat.dir = /tmp , heartbeat.interval = 3000 ");

            assertEquals(Path.of("/tmp"), args.heartbeatDir());
            assertEquals(Duration.ofMillis(3000), args.heartbeatInterval());
        }
    }
}
