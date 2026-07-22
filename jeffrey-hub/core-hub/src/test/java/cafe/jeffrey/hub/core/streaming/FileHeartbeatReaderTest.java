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

package cafe.jeffrey.hub.core.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileHeartbeatReaderTest {

    private final FileHeartbeatReader reader = new FileHeartbeatReader();

    private Path writeHeartbeatFile(Path sessionDir, String content) throws IOException {
        return writeHeartbeatDirFile(sessionDir, HeartbeatConstants.HEARTBEAT_FILE, content);
    }

    private Path writeFinishedMarkerFile(Path sessionDir, String content) throws IOException {
        return writeHeartbeatDirFile(sessionDir, HeartbeatConstants.FINISHED_FILE, content);
    }

    private Path writeHeartbeatDirFile(Path sessionDir, String fileName, String content) throws IOException {
        Path heartbeatDir = sessionDir
                .resolve(HeartbeatConstants.HEARTBEAT_DIR);
        Files.createDirectories(heartbeatDir);

        Path file = heartbeatDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    @Nested
    class ValidContent {

        @Test
        void readsEpochMillis_returnsInstant(@TempDir Path tempDir) throws IOException {
            long epochMillis = 1700000000000L;
            writeHeartbeatFile(tempDir, String.valueOf(epochMillis));

            Optional<Instant> result = reader.readLastHeartbeat(tempDir);

            assertAll(
                    () -> assertTrue(result.isPresent(), "Expected a present Optional"),
                    () -> assertEquals(Instant.ofEpochMilli(epochMillis), result.get())
            );
        }
    }

    @Nested
    class InvalidContent {

        @Test
        void missingFile_returnsEmpty(@TempDir Path tempDir) {
            Optional<Instant> result = reader.readLastHeartbeat(tempDir);

            assertTrue(result.isEmpty(), "Expected empty when heartbeat file does not exist");
        }

        @Test
        void corruptContent_returnsEmpty(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "not-a-number");

            Optional<Instant> result = reader.readLastHeartbeat(tempDir);

            assertTrue(result.isEmpty(), "Expected empty when file contains non-numeric content");
        }

        @Test
        void emptyFile_returnsEmpty(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "");

            Optional<Instant> result = reader.readLastHeartbeat(tempDir);

            assertTrue(result.isEmpty(), "Expected empty when file is empty");
        }
    }

    @Nested
    class ReadFinishedMarker {

        @Test
        void presentMarker_returnsInstant(@TempDir Path tempDir) throws IOException {
            long epochMillis = 1700000000000L;
            writeFinishedMarkerFile(tempDir, String.valueOf(epochMillis));

            Optional<Instant> result = reader.readFinishedMarker(tempDir);

            assertAll(
                    () -> assertTrue(result.isPresent(), "Expected a present Optional"),
                    () -> assertEquals(Instant.ofEpochMilli(epochMillis), result.get())
            );
        }

        @Test
        void missingMarker_returnsEmpty(@TempDir Path tempDir) {
            Optional<Instant> result = reader.readFinishedMarker(tempDir);

            assertTrue(result.isEmpty(), "Expected empty when finished marker does not exist");
        }

        @Test
        void heartbeatDoesNotLeakIntoMarker(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "1700000000000");

            Optional<Instant> result = reader.readFinishedMarker(tempDir);

            assertTrue(result.isEmpty(), "Heartbeat file must not be read as a finished marker");
        }

        @Test
        void corruptMarker_returnsEmpty(@TempDir Path tempDir) throws IOException {
            writeFinishedMarkerFile(tempDir, "not-a-number");

            Optional<Instant> result = reader.readFinishedMarker(tempDir);

            assertTrue(result.isEmpty(), "Expected empty when marker contains non-numeric content");
        }
    }
}
