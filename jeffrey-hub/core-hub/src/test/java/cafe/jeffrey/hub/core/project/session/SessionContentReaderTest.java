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
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionContentReaderTest {

    private static final String RECORDING_FILE = "profile-20261010-051046.jfr";
    private static final String ASPROF_SCRATCH_FILE = "profile-20261010-051046.jfr.1~";
    private static final String AGENT_JAR = ".jeffrey-agent.jar";
    private static final String SESSION_MARKER = ".session-info.json";

    private final SessionContentReader reader = new SessionContentReader();

    @Nested
    class Empty {

        @Test
        void aDirectoryWithNothingInIt(@TempDir Path sessionDir) {
            assertInstanceOf(SessionContentRead.Empty.class, reader.read(sessionDir));
        }

        @Test
        void aDirectoryWithOnlyWhatTheProvisionerLeavesBeforeTheJvmStarts(@TempDir Path sessionDir)
                throws IOException {
            Files.createDirectories(sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR));
            Files.writeString(sessionDir.resolve(AGENT_JAR), "jar");
            Files.writeString(sessionDir.resolve(SESSION_MARKER), "{}");

            assertInstanceOf(SessionContentRead.Empty.class, reader.read(sessionDir));
        }
    }

    @Nested
    class HoldsData {

        @Test
        void aRecordingFile(@TempDir Path sessionDir) throws IOException {
            Files.writeString(sessionDir.resolve(RECORDING_FILE), "");

            assertInstanceOf(SessionContentRead.HoldsData.class, reader.read(sessionDir));
        }

        @Test
        void anAsyncProfilerScratchFileOfZeroBytes(@TempDir Path sessionDir) throws IOException {
            // A live profiler's files read 0 B until the chunk is flushed; their presence is what counts
            Files.writeString(sessionDir.resolve(ASPROF_SCRATCH_FILE), "");

            assertInstanceOf(SessionContentRead.HoldsData.class, reader.read(sessionDir));
        }
    }

    @Nested
    class Unreadable {

        @Test
        void aMissingDirectory(@TempDir Path tempDir) {
            assertInstanceOf(SessionContentRead.Unreadable.class, reader.read(tempDir.resolve("gone")));
        }

        @Test
        void aPathThatIsAFile(@TempDir Path tempDir) throws IOException {
            Path file = Files.writeString(tempDir.resolve(RECORDING_FILE), "");

            assertInstanceOf(SessionContentRead.Unreadable.class, reader.read(file));
        }
    }
}
