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

import cafe.jeffrey.heartbeat.core.HeartbeatConfig;
import cafe.jeffrey.heartbeat.core.HeartbeatContract;
import cafe.jeffrey.heartbeat.core.HeartbeatLoop;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeffreyAgentTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration INTERVAL = Duration.ofMillis(50);

    @TempDir
    Path tempDir;

    private Optional<HeartbeatLoop> producer = Optional.empty();

    @AfterEach
    void tearDown() {
        producer.ifPresent(HeartbeatLoop::close);
        System.clearProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY);
    }

    @Nested
    class Beating {

        @Test
        void writesTheHeartbeatFileWithTheClocksTime() {
            Path directory = tempDir.resolve(".heartbeat");

            producer = JeffreyAgent.start(new HeartbeatConfig(directory, INTERVAL, true), CLOCK);

            assertTrue(producer.isPresent());
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> Files.exists(directory.resolve(HeartbeatContract.HEARTBEAT_FILE)));
            assertEquals(Long.toString(NOW.toEpochMilli()), read(directory.resolve(HeartbeatContract.HEARTBEAT_FILE)));
        }

        @Test
        void marksTheJvmSoTheLibraryStandsDown() {
            producer = JeffreyAgent.start(new HeartbeatConfig(tempDir, INTERVAL, true), CLOCK);

            assertEquals("true", System.getProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY));
        }

        @Test
        void writesTheFinishedMarkerOnCloseAndNoScratchFile() {
            producer = JeffreyAgent.start(new HeartbeatConfig(tempDir, INTERVAL, true), CLOCK);

            producer.orElseThrow().close();

            assertEquals(Long.toString(NOW.toEpochMilli()), read(tempDir.resolve(HeartbeatContract.FINISHED_FILE)));
            assertFalse(Files.exists(tempDir.resolve(HeartbeatContract.HEARTBEAT_FILE + ".tmp")));
        }
    }

    @Nested
    class StandingDown {

        @Test
        void startsNothingWhenTheApplicationSwitchedItOff() {
            producer = JeffreyAgent.start(new HeartbeatConfig(tempDir, INTERVAL, false), CLOCK);

            assertTrue(producer.isEmpty());
            assertNull(System.getProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY));
        }

        @Test
        void startsNothingWithoutADirectory() {
            producer = JeffreyAgent.start(new HeartbeatConfig(null, INTERVAL, true), CLOCK);

            assertTrue(producer.isEmpty());
            assertNull(System.getProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY));
        }

        /** The library may still report from elsewhere, so the JVM is not marked. */
        @Test
        void startsNothingWhenTheDirectoryCannotBeCreated() throws IOException {
            Path file = Files.createFile(tempDir.resolve("not-a-directory"));

            producer = JeffreyAgent.start(new HeartbeatConfig(file.resolve("child"), INTERVAL, true), CLOCK);

            assertTrue(producer.isEmpty());
            assertNull(System.getProperty(HeartbeatContract.AGENT_ACTIVE_PROPERTY));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
