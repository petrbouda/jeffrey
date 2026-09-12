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

import cafe.jeffrey.hub.core.project.repository.file.AsprofFileInfoProcessor;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AsprofFileRepositoryStorageTest {

    /**
     * Which files cost a round trip to the share. Every open here is paid per file, per session,
     * on every listing of the project, so the cases that read from the listing are as much the
     * contract as the case that does not.
     */
    @Nested
    class SizeReader {

        @Test
        void opensTheRecordingOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR));
        }

        @Test
        void opensTheLogsAndCachesOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JVM_LOG));
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.ASPROF_TEMP));
        }

        @Test
        void readsACompressedRecordingFromTheListingEvenWhileTheSessionRecords() {
            // This hub wrote and closed it, so no other client holds it open and the listing is
            // final. A long session accumulates one of these every chunk, and opening each would
            // grow the cost of a listing without bound.
            assertSame(FileSizeReader.FILE_ATTRIBUTES,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR_LZ4));
        }

        @Test
        void readsEveryFileOfAFinishedSessionFromTheListing() {
            for (SupportedRecordingFile fileType : SupportedRecordingFile.values()) {
                assertSame(FileSizeReader.FILE_ATTRIBUTES,
                        AsprofFileRepositoryStorage.sizeReader(RecordingStatus.FINISHED, fileType),
                        "file type: " + fileType);
            }
        }
    }

    @Nested
    class Describe {

        private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);

        @TempDir
        Path workspace;

        private AsprofFileRepositoryStorage storage() {
            return new AsprofFileRepositoryStorage(
                    mock(ProjectInfo.class),
                    workspace,
                    workspace.resolve("temp"),
                    mock(ProjectRepositoryRepository.class),
                    new AsprofFileInfoProcessor());
        }

        private Path sessionDir() throws IOException {
            return Files.createDirectories(workspace.resolve("project/instance/session"));
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Path session = sessionDir();
            Path file = Files.write(session.resolve("gc-jvm.log"), CONTENT);

            RepositoryFile described = storage().describe(file, RecordingStatus.ACTIVE, workspace, session);

            assertNotNull(described);
            assertEquals("gc-jvm.log", described.name());
            assertEquals(CONTENT.length, described.size());
            assertEquals(SupportedRecordingFile.JVM_LOG, described.fileType());
        }

        @Test
        void leavesOutAFileThatWentAwayWhileTheSessionWasListed() throws IOException {
            // async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
            // listing has just read can be gone before its size is asked for. The next listing
            // will not mention it either; failing here would take the instance page down with it.
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr.1~");

            assertNull(storage().describe(vanished, RecordingStatus.ACTIVE, workspace, session));
        }

        @Test
        void leavesOutAVanishedFileOfAFinishedSessionToo() throws IOException {
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr");

            assertNull(storage().describe(vanished, RecordingStatus.FINISHED, workspace, session));
        }
    }
}
