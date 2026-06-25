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

package cafe.jeffrey.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

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

    @Nested
    class AppInfo {

        private static String base64(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void noAppArgsYieldsNullAppInfo() {
            AgentArgs args = AgentArgs.parse("heartbeat.dir=/tmp");

            assertNull(args.appInfo());
        }

        @Test
        void sessionIdIsRequiredForAppInfo() {
            AgentArgs args = AgentArgs.parse("app.workspaceId=$default,app.projectId=p-1");

            assertNull(args.appInfo());
        }

        @Test
        void parsesFullIdentity() {
            String parsed = String.join(",",
                    "heartbeat.dir=/tmp/.heartbeat",
                    "app.workspaceId=$default",
                    "app.projectId=11111111-2222-3333-4444-555555555555",
                    "app.projectName=payments-api",
                    "app.projectLabel=" + base64("Payments API (prod)"),
                    "app.instanceId=pod-7c9",
                    "app.sessionId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                    "app.sessionOrder=3",
                    "app.attributes=" + base64("cluster=eu;namespace=prod"),
                    "app.provisionedAt=1700000000000");

            AppInformation info = AgentArgs.parse(parsed).appInfo();

            assertNotNull(info);
            assertEquals("$default", info.workspaceId());
            assertEquals("11111111-2222-3333-4444-555555555555", info.projectId());
            assertEquals("payments-api", info.projectName());
            assertEquals("Payments API (prod)", info.projectLabel());
            assertEquals("pod-7c9", info.instanceId());
            assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", info.sessionId());
            assertEquals(3, info.sessionOrder());
            assertEquals("cluster=eu;namespace=prod", info.attributes());
            assertEquals(1700000000000L, info.provisionedAt());
            // Heartbeat parsing is unaffected.
            assertEquals(Path.of("/tmp/.heartbeat"), AgentArgs.parse(parsed).heartbeatDir());
        }

        @Test
        void base64ValuesSurviveDelimiterCharacters() {
            // A label and attributes containing ',' and ';' would break the
            // comma-separated agent-arg parsing if not Base64-encoded.
            String label = "Team A, B & C; v2";
            String attributes = "region=eu-west,1;team=core,platform";
            String parsed = String.join(",",
                    "app.sessionId=s-1",
                    "app.projectLabel=" + base64(label),
                    "app.attributes=" + base64(attributes));

            AppInformation info = AgentArgs.parse(parsed).appInfo();

            assertNotNull(info);
            assertEquals(label, info.projectLabel());
            assertEquals(attributes, info.attributes());
        }
    }
}
