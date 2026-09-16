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
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsprofFileRepositoryStorageTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

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
                    mock(ProjectRepositoryRepository.class),
                    new AsprofFileInfoProcessor());
        }

        private Path sessionDir() throws IOException {
            return Files.createDirectories(workspace.resolve("project/instance/session"));
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Path session = sessionDir();
            Path file = Files.write(session.resolve("gc.jvm-log"), CONTENT);

            RepositoryFile described = storage().describe(file, RecordingStatus.ACTIVE, session);

            assertNotNull(described);
            assertEquals("gc.jvm-log", described.name());
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

            assertNull(storage().describe(vanished, RecordingStatus.ACTIVE, session));
        }

        @Test
        void leavesOutAVanishedFileOfAFinishedSessionToo() throws IOException {
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr");

            assertNull(storage().describe(vanished, RecordingStatus.FINISHED, session));
        }
    }

    /**
     * Deleting the files a caller named by the ids the listing gave them. Both retention jobs and
     * the UI's delete come through here, and an id that does not find its file frees nothing while
     * reporting success.
     */
    @Nested
    class DeleteRepositoryFiles {

        private static final String SESSION_ID = "session-1";
        private static final String PROJECT = "project";
        private static final String INSTANCE = "instance";

        @TempDir
        Path workspacesDir;

        private final ProjectRepositoryRepository repository = mock(ProjectRepositoryRepository.class);

        private Path sessionDir() throws IOException {
            return Files.createDirectories(
                    workspacesDir.resolve("ws").resolve(PROJECT).resolve(INSTANCE).resolve(SESSION_ID));
        }

        private AsprofFileRepositoryStorage storage() {
            when(repository.getAll()).thenReturn(List.of(new RepositoryInfo(
                    "repo-1", RepositoryType.ASYNC_PROFILER, null, "ws", PROJECT)));
            when(repository.findSessionById(SESSION_ID)).thenReturn(Optional.of(new ProjectInstanceSessionInfo(
                    SESSION_ID, "repo-1", INSTANCE, 0, Path.of(INSTANCE, SESSION_ID),
                    T0, T0, null, false, false, null)));

            return new AsprofFileRepositoryStorage(
                    mock(ProjectInfo.class), workspacesDir, repository, new AsprofFileInfoProcessor());
        }

        private static Path write(Path dir, String name) throws IOException {
            return Files.write(dir.resolve(name), "x".getBytes(StandardCharsets.UTF_8));
        }

        /**
         * The id is the name with the recording extension stripped, so it cannot be turned back
         * into a path by appending it to the session directory — which is what this did, and why
         * it deleted nothing at all.
         */
        @Test
        void deletesAChunkNamedByItsId() throws IOException {
            Path session = sessionDir();
            Path chunk = write(session, "profile-20260220-120000.jfr");
            Path kept = write(session, "profile-20260220-120500.jfr");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("profile-20260220-120000"));

            assertFalse(Files.exists(chunk));
            assertTrue(Files.exists(kept), "only the file that was named goes");
        }

        /**
         * The id survives the hub compressing the file — that is what stripping the extension is
         * for — so an id taken from a listing before the compression still names the chunk after.
         */
        @Test
        void deletesTheCompressedFormUnderTheSameId() throws IOException {
            Path session = sessionDir();
            Path archive = write(session, "profile-20260220-120000.jfr.lz4");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("profile-20260220-120000"));

            assertFalse(Files.exists(archive));
        }

        @Test
        void deletesAnArtifactNamedByItsId() throws IOException {
            Path session = sessionDir();
            Path log = write(session, "service-app.log");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("service-app.log"));

            assertFalse(Files.exists(log));
        }

        @Test
        void leavesTheSessionAloneForAnIdItDoesNotHold() throws IOException {
            Path session = sessionDir();
            Path chunk = write(session, "profile-20260220-120000.jfr");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("profile-20260220-999999"));

            assertTrue(Files.exists(chunk));
        }

        /**
         * The ids arrive over gRPC. Resolving one against the session directory let a {@code ../}
         * walk out of it, and every file the hub could delete was one it would delete. Matching
         * against the directory's own entries leaves nothing to resolve.
         */
        @Test
        void cannotReachAFileOutsideTheSessionDirectory() throws IOException {
            Path session = sessionDir();
            Path outside = write(workspacesDir, "keep-me.txt");

            // Exactly the depth the old resolve() needed: the session sits four levels under the
            // workspaces directory, so this id named that file and deleteIfExists removed it.
            assertEquals(workspacesDir, session.resolve("../../../..").normalize());
            storage().deleteRepositoryFiles(SESSION_ID, List.of("../../../../keep-me.txt"));

            assertTrue(Files.exists(outside), "an id is matched against the session's files, not resolved to a path");
        }
    }
}
