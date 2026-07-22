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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatProducerTest {

    private static final String HEARTBEAT_FILE = "heartbeat";
    private static final String HEARTBEAT_TMP_FILE = "heartbeat.tmp";
    private static final String FINISHED_FILE = "finished";
    private static final String FINISHED_TMP_FILE = "finished.tmp";

    private static long parseEpochMillis(Path file) throws IOException {
        return Long.parseLong(Files.readString(file).strip());
    }

    @Nested
    class Beating {

        @Test
        void writesHeartbeatFileWithEpochMillis(@TempDir Path heartbeatDir) {
            try (HeartbeatProducer producer = new HeartbeatProducer(heartbeatDir, Duration.ofMillis(50))) {
                producer.start();

                Path heartbeatFile = heartbeatDir.resolve(HEARTBEAT_FILE);
                await().atMost(5, SECONDS).untilAsserted(() -> {
                    assertTrue(Files.exists(heartbeatFile), "Heartbeat file must be written");
                    assertTrue(parseEpochMillis(heartbeatFile) > 0, "Heartbeat must contain epoch millis");
                });
            }
        }
    }

    @Nested
    class CleanExit {

        @Test
        void closeWritesFinishedMarker(@TempDir Path heartbeatDir) throws IOException {
            HeartbeatProducer producer = new HeartbeatProducer(heartbeatDir, Duration.ofMillis(50));
            producer.start();

            await().atMost(5, SECONDS).untilAsserted(
                    () -> assertTrue(Files.exists(heartbeatDir.resolve(HEARTBEAT_FILE))));

            producer.close();

            Path finishedFile = heartbeatDir.resolve(FINISHED_FILE);
            assertTrue(Files.exists(finishedFile), "Finished marker must be written on close");
            assertTrue(parseEpochMillis(finishedFile) > 0, "Finished marker must contain epoch millis");
            assertFalse(Files.exists(heartbeatDir.resolve(HEARTBEAT_TMP_FILE)), "Heartbeat tmp file must be cleaned up");
            assertFalse(Files.exists(heartbeatDir.resolve(FINISHED_TMP_FILE)), "Finished tmp file must not remain");
        }

        @Test
        void closeWithoutStartStillWritesFinishedMarker(@TempDir Path heartbeatDir) {
            HeartbeatProducer producer = new HeartbeatProducer(heartbeatDir, Duration.ofSeconds(5));

            producer.close();

            assertTrue(Files.exists(heartbeatDir.resolve(FINISHED_FILE)),
                    "Finished marker must be written even when the producer never started");
        }
    }
}
