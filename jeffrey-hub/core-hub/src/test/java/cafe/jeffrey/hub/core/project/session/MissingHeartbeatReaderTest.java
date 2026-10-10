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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.model.repository.MissingHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MissingHeartbeatReaderTest {

    private final MissingHeartbeatReader reader = new MissingHeartbeatReader();

    @Test
    void nothingForASessionThatSentAHeartbeat(@TempDir Path sessionDir) throws IOException {
        Files.writeString(sessionDir.resolve(HeartbeatConstants.AGENT_JAR_FILE), "jar");

        assertNull(reader.read(false, sessionDir));
    }

    @Test
    void agentPresent_whenTheProvisionerWroteTheAgent(@TempDir Path sessionDir) throws IOException {
        Files.writeString(sessionDir.resolve(HeartbeatConstants.AGENT_JAR_FILE), "jar");

        assertEquals(new MissingHeartbeat(true), reader.read(true, sessionDir));
    }

    @Test
    void agentAbsent_whenTheAgentWasSwitchedOff(@TempDir Path sessionDir) {
        assertEquals(new MissingHeartbeat(false), reader.read(true, sessionDir));
    }
}
