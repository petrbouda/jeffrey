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

package cafe.jeffrey.hub.core.project.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

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

            LivenessRead result = reader.readLastHeartbeat(tempDir);

            assertEquals(new LivenessRead.Reported(Instant.ofEpochMilli(epochMillis)), result);
        }

        @Test
        void toleratesSurroundingWhitespace(@TempDir Path tempDir) throws IOException {
            long epochMillis = 1700000000000L;
            writeHeartbeatFile(tempDir, "  " + epochMillis + "\n");

            LivenessRead result = reader.readLastHeartbeat(tempDir);

            assertEquals(new LivenessRead.Reported(Instant.ofEpochMilli(epochMillis)), result);
        }
    }

    /**
     * Absence is evidence — it is what lets the finisher conclude a declared producer never
     * reported. Every one of these must read as {@code Absent} and not as a failed read.
     */
    @Nested
    class NothingWritten {

        @Test
        void missingFile_isAbsent(@TempDir Path tempDir) {
            assertEquals(LivenessRead.absent(), reader.readLastHeartbeat(tempDir));
        }

        @Test
        void missingHeartbeatDirectory_isAbsent(@TempDir Path tempDir) {
            Path sessionWithoutHeartbeatDir = tempDir.resolve("session");

            assertEquals(LivenessRead.absent(), reader.readLastHeartbeat(sessionWithoutHeartbeatDir));
        }

        @Test
        void missingMarker_isAbsent(@TempDir Path tempDir) {
            assertEquals(LivenessRead.absent(), reader.readFinishedMarker(tempDir));
        }

        @Test
        void heartbeatDoesNotLeakIntoMarker(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "1700000000000");

            assertEquals(LivenessRead.absent(), reader.readFinishedMarker(tempDir),
                    "Heartbeat file must not be read as a finished marker");
        }
    }

    /**
     * A file that is there and makes no sense is the opposite of absence: the producer may well
     * be running. Reporting it as absence is what lets one unreadable file finish a live session
     * at its own start timestamp — see {@link SessionFinisher#tryFinishFromHeartbeat}.
     */
    @Nested
    class PresentButUnreadable {

        @Test
        void corruptContent_isUnreadable(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "not-a-number");

            LivenessRead result = reader.readLastHeartbeat(tempDir);

            assertAll(
                    () -> assertInstanceOf(LivenessRead.Unreadable.class, result),
                    () -> assertFalse(result.isAbsent(), "Corrupt content must not read as absence"),
                    () -> assertTrue(result.timestamp().isEmpty())
            );
        }

        @Test
        void emptyFile_isUnreadable(@TempDir Path tempDir) throws IOException {
            writeHeartbeatFile(tempDir, "");

            LivenessRead result = reader.readLastHeartbeat(tempDir);

            assertAll(
                    () -> assertInstanceOf(LivenessRead.Unreadable.class, result),
                    () -> assertFalse(result.isAbsent(), "An empty file must not read as absence")
            );
        }

        @Test
        void corruptMarker_isUnreadable(@TempDir Path tempDir) throws IOException {
            writeFinishedMarkerFile(tempDir, "not-a-number");

            assertInstanceOf(LivenessRead.Unreadable.class, reader.readFinishedMarker(tempDir));
        }

        @Test
        void directoryInPlaceOfFile_isUnreadable(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir
                    .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                    .resolve(HeartbeatConstants.HEARTBEAT_FILE));

            LivenessRead result = reader.readLastHeartbeat(tempDir);

            assertAll(
                    () -> assertInstanceOf(LivenessRead.Unreadable.class, result),
                    () -> assertFalse(result.isAbsent(), "An unreadable path must not read as absence")
            );
        }
    }
}
