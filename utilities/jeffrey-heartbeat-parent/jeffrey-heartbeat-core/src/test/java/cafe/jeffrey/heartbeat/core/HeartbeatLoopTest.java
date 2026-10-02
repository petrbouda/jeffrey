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
package cafe.jeffrey.heartbeat.core;

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
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatLoopTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration FAST = Duration.ofMillis(20);

    @TempDir
    Path tempDir;

    @Nested
    class Beating {

        @Test
        void createsTheDirectoryAndWritesTheClocksTime() {
            Path directory = tempDir.resolve("nested").resolve(HeartbeatContract.DIRECTORY);

            try (HeartbeatLoop loop = HeartbeatLoop.start(directory, FAST, CLOCK, RecordingLog.SILENT).orElseThrow()) {
                assertTrue(loop.running());
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(
                        Long.toString(NOW.toEpochMilli()),
                        Files.readString(directory.resolve(HeartbeatContract.HEARTBEAT_FILE))));
            }
        }
    }

    @Nested
    class Closing {

        @Test
        void writesTheFinishedMarkerAndNoScratchFile() throws IOException {
            HeartbeatLoop loop = HeartbeatLoop.start(tempDir, FAST, CLOCK, RecordingLog.SILENT).orElseThrow();

            loop.close();

            assertFalse(loop.running());
            assertEquals(Long.toString(NOW.toEpochMilli()),
                    Files.readString(tempDir.resolve(HeartbeatContract.FINISHED_FILE)));
            assertFalse(Files.exists(tempDir.resolve(HeartbeatContract.HEARTBEAT_FILE + ".tmp")));
        }

        @Test
        void closingTwiceIsHarmless() {
            HeartbeatLoop loop = HeartbeatLoop.start(tempDir, FAST, CLOCK, RecordingLog.SILENT).orElseThrow();

            loop.close();
            loop.close();

            assertFalse(loop.running());
        }
    }

    @Nested
    class FailsOpen {

        @Test
        void whenTheDirectoryCannotBeCreated() throws IOException {
            Path blocked = Files.writeString(tempDir.resolve("blocked"), "not a directory");
            RecordingLog log = new RecordingLog();

            Optional<HeartbeatLoop> loop = HeartbeatLoop.start(blocked.resolve("child"), FAST, CLOCK, log);

            assertTrue(loop.isEmpty());
            assertEquals(1, log.warnings().size());
        }
    }
}
