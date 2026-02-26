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

package pbouda.jeffrey.platform.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileHeartbeatReaderTest {

    private final FileHeartbeatReader reader = new FileHeartbeatReader();

    private Path writeHeartbeatFile(Path sessionDir, String content) throws IOException {
        Path heartbeatDir = sessionDir
                .resolve(HeartbeatConstants.HEARTBEAT_DIR);
        Files.createDirectories(heartbeatDir);

        Path heartbeatFile = heartbeatDir.resolve(HeartbeatConstants.HEARTBEAT_FILE);
        Files.writeString(heartbeatFile, content);
        return heartbeatFile;
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
}
