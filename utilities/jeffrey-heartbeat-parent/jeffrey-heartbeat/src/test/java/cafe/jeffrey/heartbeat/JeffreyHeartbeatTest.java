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

package cafe.jeffrey.heartbeat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class JeffreyHeartbeatTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration FAST = Duration.ofMillis(20);

    private static Path heartbeatFile(Path directory) {
        return directory.resolve(HeartbeatFiles.HEARTBEAT_FILE);
    }

    private static Path finishedFile(Path directory) {
        return directory.resolve(HeartbeatFiles.FINISHED_FILE);
    }

    @Nested
    class Beating {

        @Test
        void writesTheTimestampAndKeepsRewritingIt(@TempDir Path tempDir) {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);

            try (JeffreyHeartbeat heartbeat =
                         JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, true), FIXED)) {

                assertTrue(heartbeat.running());
                await().atMost(5, SECONDS).untilAsserted(() ->
                        assertEquals(Long.toString(NOW.toEpochMilli()),
                                Files.readString(heartbeatFile(directory))));
            }
        }

        @Test
        void createsTheDirectoryItWasGiven(@TempDir Path tempDir) {
            Path directory = tempDir.resolve("nested").resolve(HeartbeatFiles.DIRECTORY);

            try (JeffreyHeartbeat heartbeat =
                         JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, true), FIXED)) {

                assertTrue(heartbeat.running());
                assertTrue(Files.isDirectory(directory));
            }
        }

        @Test
        void leavesNoScratchFileBehind(@TempDir Path tempDir) throws IOException {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);

            try (JeffreyHeartbeat ignored =
                         JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, true), FIXED)) {
                await().atMost(5, SECONDS).until(() -> Files.exists(heartbeatFile(directory)));
            }

            try (var entries = Files.list(directory)) {
                assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                        "a rename, not a write in place, is what makes the file safe to read unlocked");
            }
        }
    }

    @Nested
    class CleanExit {

        @Test
        void closeWritesTheFinishedMarker(@TempDir Path tempDir) throws IOException {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);
            JeffreyHeartbeat heartbeat =
                    JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, true), FIXED);

            heartbeat.close();

            assertEquals(Long.toString(NOW.toEpochMilli()), Files.readString(finishedFile(directory)));
        }

        @Test
        void closingTwiceIsHarmless(@TempDir Path tempDir) {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);
            JeffreyHeartbeat heartbeat =
                    JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, true), FIXED);

            heartbeat.close();
            assertDoesNotThrow(heartbeat::close);
        }
    }

    @Nested
    class Inert {

        @Test
        void whenNoDirectoryWasResolved() {
            JeffreyHeartbeat heartbeat =
                    JeffreyHeartbeat.start(new HeartbeatSettings(null, FAST, true), FIXED);

            assertFalse(heartbeat.running());
            assertDoesNotThrow(heartbeat::close);
        }

        @Test
        void whenTheProvisionerSaidTheAgentIsBeating(@TempDir Path tempDir) {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);

            JeffreyHeartbeat heartbeat =
                    JeffreyHeartbeat.start(new HeartbeatSettings(directory, FAST, false), FIXED);

            assertFalse(heartbeat.running());
            assertFalse(Files.exists(directory), "a disabled heartbeat creates nothing at all");
        }

        @Test
        void whenTheDirectoryCannotBeCreated(@TempDir Path tempDir) throws IOException {
            // A regular file where the directory should go: createDirectories fails, and the
            // application must still start
            Path blocked = tempDir.resolve("blocked");
            Files.writeString(blocked, "not a directory");

            JeffreyHeartbeat heartbeat = JeffreyHeartbeat.start(
                    new HeartbeatSettings(blocked.resolve(HeartbeatFiles.DIRECTORY), FAST, true), FIXED);

            assertFalse(heartbeat.running());
            assertDoesNotThrow(heartbeat::close);
        }
    }
}
