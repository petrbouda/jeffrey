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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * When a file was opened — the timestamp a session is ordered by, so which chunk is still open,
 * which chunks a window covers and which files retention has aged all rest on it.
 */
class TimestampResolverTest {

    @TempDir
    Path dir;

    private Path write(String name) throws IOException {
        return Files.write(dir.resolve(name), "x".getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    class EachTypeSaysWhereItsTimestampComesFrom {

        @Test
        void aRecordingReadsItsOwnName() {
            assertSame(TimestampResolver.RECORDING_NAME, HubManagedFile.JFR.timestampResolver());
            assertSame(TimestampResolver.RECORDING_NAME, HubManagedFile.JFR_LZ4.timestampResolver());
        }
    }

    @Nested
    class FromTheName {

        @Test
        void readsTheInstantTheProfilerWroteIntoIt() {
            assertEquals(
                    Instant.parse("2026-02-20T12:05:00Z"),
                    TimestampResolver.RECORDING_NAME.resolve(dir.resolve("profile-20260220-120500.jfr")));
        }

        /**
         * The reason a JFR may be compressed at all. The archive is a new file whose creation
         * time is the moment the job ran — minutes or hours after the profiler opened the chunk,
         * and later than the chunk still open beside it. Read that way the archive looks like the
         * session's newest recording and takes the open chunk's place, at which point the
         * compression job compresses the file the profiler is writing and both retention jobs
         * consider it deletable.
         */
        @Test
        void isUnchangedByTheFileBeingCompressed() {
            assertEquals(
                    TimestampResolver.RECORDING_NAME.resolve(dir.resolve("profile-20260220-120500.jfr")),
                    TimestampResolver.RECORDING_NAME.resolve(dir.resolve("profile-20260220-120500.jfr.lz4")));
        }
    }

    @Nested
    class FallingBackToTheFilesystem {

        @Test
        void forARecordingNamedWithAnotherPrefix() throws IOException {
            Path file = write("recording-20260220-120500.jfr");

            assertEquals(FileSystemUtils.createdAt(file), TimestampResolver.RECORDING_NAME.resolve(file));
        }

        /**
         * A name that does not parse must not raise: the caller drops a file it cannot describe,
         * so one oddly named file would leave the listing entirely rather than sort imprecisely.
         */
        @Test
        void forATimestampThatDoesNotParse() throws IOException {
            Path file = write("profile-not-a-timestamp.jfr");

            assertEquals(FileSystemUtils.createdAt(file), TimestampResolver.RECORDING_NAME.resolve(file));
        }

        @Test
        void forAFileThatIsNotARecording() throws IOException {
            Path log = write("service-app.log");

            assertEquals(FileSystemUtils.createdAt(log), TimestampResolver.FILESYSTEM.resolve(log));
        }
    }
}
