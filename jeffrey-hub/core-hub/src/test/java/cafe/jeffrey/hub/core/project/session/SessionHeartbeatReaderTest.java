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

package cafe.jeffrey.hub.core.project.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.model.repository.SessionHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionHeartbeatReaderTest {

    private static final Instant HEARTBEAT = Instant.parse("2026-10-10T05:20:00Z");

    private final SessionHeartbeatReader reader = new SessionHeartbeatReader(new FileHeartbeatReader());

    private static void writeHeartbeat(Path sessionDir) throws IOException {
        Path dir = Files.createDirectories(sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR));
        Files.writeString(dir.resolve(HeartbeatConstants.HEARTBEAT_FILE), String.valueOf(HEARTBEAT.toEpochMilli()));
    }

    private static void writeAgentJar(Path sessionDir) throws IOException {
        Files.writeString(sessionDir.resolve(HeartbeatConstants.AGENT_JAR_FILE), "jar");
    }

    @Nested
    class LastHeartbeat {

        @Test
        void isReadForAnOpenSession(@TempDir Path sessionDir) throws IOException {
            writeHeartbeat(sessionDir);

            SessionHeartbeat heartbeat = reader.read(false, false, sessionDir);

            assertFalse(heartbeat.missing());
            assertEquals(HEARTBEAT, heartbeat.lastHeartbeatAt());
        }

        @Test
        void isNotShownForAFinishedSession(@TempDir Path sessionDir) throws IOException {
            writeHeartbeat(sessionDir);

            assertNull(reader.read(false, true, sessionDir).lastHeartbeatAt());
        }

        @Test
        void isAbsentWhenNothingWasWritten(@TempDir Path sessionDir) {
            assertNull(reader.read(true, false, sessionDir).lastHeartbeatAt());
        }
    }

    @Nested
    class AgentPresence {

        @Test
        void isReportedForASessionMissingItsHeartbeat(@TempDir Path sessionDir) throws IOException {
            writeAgentJar(sessionDir);

            SessionHeartbeat heartbeat = reader.read(true, false, sessionDir);

            assertTrue(heartbeat.missing());
            assertTrue(heartbeat.agentPresent());
        }

        @Test
        void isFalseWhenTheAgentWasNotWritten(@TempDir Path sessionDir) {
            assertFalse(reader.read(true, false, sessionDir).agentPresent());
        }

        @Test
        void isNotLookedForWhenTheHeartbeatIsThere(@TempDir Path sessionDir) throws IOException {
            writeAgentJar(sessionDir);
            writeHeartbeat(sessionDir);

            assertFalse(reader.read(false, false, sessionDir).agentPresent());
        }
    }

    @Test
    void fromRowCarriesOnlyTheFlag() {
        assertEquals(new SessionHeartbeat(true, null, false), SessionHeartbeatReader.fromRow(true));
    }
}
