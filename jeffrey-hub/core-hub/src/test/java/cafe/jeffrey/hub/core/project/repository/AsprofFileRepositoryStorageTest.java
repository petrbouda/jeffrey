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
import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedFile.JFR));
        }

        @Test
        void opensTheLogsAndCachesOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedFile.JVM_LOG));
            assertSame(FileSizeReader.LIVE_FILE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedFile.ASPROF_TEMP));
        }

        @Test
        void readsACompressedRecordingFromTheListingEvenWhileTheSessionRecords() {
            // This hub wrote and closed it, so no other client holds it open and the listing is
            // final. A long session accumulates one of these every chunk, and opening each would
            // grow the cost of a listing without bound.
            assertSame(FileSizeReader.FILE_ATTRIBUTES,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedFile.JFR_LZ4));
        }

        @Test
        void readsEveryFileOfAFinishedSessionFromTheListing() {
            for (SupportedFile fileType : SupportedFile.values()) {
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

            RepositoryFile described = storage().describe(file, RecordingStatus.ACTIVE, workspace, session);

            assertNotNull(described);
            assertEquals("gc.jvm-log", described.name());
            assertEquals(CONTENT.length, described.size());
            assertEquals(SupportedFile.JVM_LOG, described.fileType());
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

    /**
     * The storage over a real session directory, listed through the database rows the
     * repository would hold. Every method here resolves a file id through that listing rather
     * than against the directory — a chunk's id is its name without the extension, so a path
     * built from the id names nothing.
     */
    @Nested
    class OverASession {

        private static final String SESSION_ID = "session";
        private static final byte[] CHUNK = "chunk-bytes".getBytes(StandardCharsets.UTF_8);

        @TempDir
        Path workspace;

        private Path session;
        private AsprofFileRepositoryStorage storage;

        @BeforeEach
        void setUp() throws IOException {
            session = Files.createDirectories(workspace.resolve("project/instance/session"));
            ProjectRepositoryRepository repository = mock(ProjectRepositoryRepository.class);
            when(repository.getAll()).thenReturn(List.of(
                    new RepositoryInfo("repo", RepositoryType.ASYNC_PROFILER, null, "", "project")));
            when(repository.findSessionById(SESSION_ID)).thenReturn(Optional.of(new ProjectInstanceSessionInfo(
                    SESSION_ID, "repo", "instance", 0, Path.of("instance/session"),
                    Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, false, false)));
            when(repository.findLatestSessionId()).thenReturn(Optional.of(SESSION_ID));
            ProjectInfo project = mock(ProjectInfo.class);
            when(project.id()).thenReturn("project");
            storage = new AsprofFileRepositoryStorage(project, workspace, repository, new AsprofFileInfoProcessor());
        }

        private Path write(String name) throws IOException {
            return Files.write(session.resolve(name), CHUNK);
        }

        private String idOf(String name) {
            return storage.singleSession(SESSION_ID, true).orElseThrow().files().stream()
                    .filter(file -> file.name().equals(name))
                    .map(RepositoryFile::id)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("not listed: " + name));
        }

        @Test
        void listsEveryFileAndSaysWhichAreDownloadable() throws IOException {
            write("profile-20260101-120000.jfr");
            write("gc.jvm-log");
            write("notes.txt");
            write("profile-20260101-120000.jfr.1~");

            List<RepositoryFile> files = storage.singleSession(SESSION_ID, true).orElseThrow().files();

            assertEquals(4, files.size());
            for (RepositoryFile file : files) {
                boolean transientFile = file.fileType() == SupportedFile.ASPROF_TEMP;
                assertEquals(!transientFile, file.isDownloadable(), file.name());
            }
        }

        @Test
        void listsTheChunksOldestFirstAndNothingElse() throws IOException {
            Path later = write("profile-20260101-130000.jfr");
            Path earlier = write("profile-20260101-120000.jfr");
            write("heap-dump.hprof");
            write("gc.jvm-log");

            assertEquals(List.of(earlier, later), storage.finishedChunks(SESSION_ID));
            assertEquals(Optional.of(later), storage.latestFinishedChunk(SESSION_ID));
        }

        @Test
        void compressesOnlyTheChunks() throws IOException {
            Path chunk = write("profile-20260101-120000.jfr");
            Path dump = write("heap-dump.hprof");

            assertEquals(1, storage.compressSession(SESSION_ID));

            assertFalse(Files.exists(chunk), "the raw chunk is replaced by its archive");
            assertTrue(Lz4Compressor.isLz4Compressed(session.resolve("profile-20260101-120000.jfr.lz4")));
            assertTrue(Files.exists(dump), "a heap dump is left alone");
            assertEquals(1, storage.compressSession(SESSION_ID), "a second pass finds the chunk compressed and leaves it");
            assertEquals(List.of(session.resolve("profile-20260101-120000.jfr.lz4")), storage.finishedChunks(SESSION_ID));
        }

        @Test
        void aChunkKeepsItsIdAcrossCompression() throws IOException {
            write("profile-20260101-120000.jfr");
            String before = idOf("profile-20260101-120000.jfr");

            storage.compressSession(SESSION_ID);

            assertEquals(before, idOf("profile-20260101-120000.jfr.lz4"));
        }

        /**
         * Compression renames a chunk, and the date the listing reports for it must not move
         * with the rename: dated by the file system, a compressed chunk would be dated by the
         * compression — after every chunk still raw — and a recording assembled oldest-first
         * would put its oldest chunk last.
         */
        @Test
        void aChunkKeepsItsDateAcrossCompression() throws IOException {
            write("profile-20260101-120000.jfr");
            Instant before = createdAtOf("profile-20260101-120000.jfr");

            storage.compressSession(SESSION_ID);

            assertEquals(Instant.parse("2026-01-01T12:00:00Z"), before);
            assertEquals(before, createdAtOf("profile-20260101-120000.jfr.lz4"));
        }

        @Test
        void listsAnOlderCompressedChunkBeforeANewerRawOne() throws IOException {
            write("profile-20260101-120000.jfr");
            storage.compressSession(SESSION_ID);
            Path newer = write("profile-20260101-130000.jfr");

            assertEquals(List.of(session.resolve("profile-20260101-120000.jfr.lz4"), newer),
                    storage.finishedChunks(SESSION_ID));
        }

        /**
         * The raw chunk and its archive lie side by side for a moment; a listing taken then
         * still speaks of one chunk, and of the archive, which is the complete one.
         */
        @Test
        void aChunkListedInBothFormsIsOneChunk() throws IOException {
            Path raw = write("profile-20260101-120000.jfr");
            Lz4Compressor.compress(raw, session.resolve("profile-20260101-120000.jfr.lz4"));

            List<Path> chunks = storage.finishedChunks(SESSION_ID);

            assertEquals(List.of(session.resolve("profile-20260101-120000.jfr.lz4")), chunks);
            assertEquals(2, storage.singleSession(SESSION_ID, true).orElseThrow().files().size(),
                    "the listing itself still reports both files");
        }

        @Test
        void compressesThroughAHiddenPartialFileAndLeavesNoneBehind() throws IOException {
            write("profile-20260101-120000.jfr");

            storage.compressSession(SESSION_ID);

            try (Stream<Path> files = Files.list(session)) {
                assertEquals(List.of(session.resolve("profile-20260101-120000.jfr.lz4")), files.toList());
            }
        }

        /**
         * A link in a session directory is not one of the session's files: the producer side of
         * a shared volume could otherwise name any file on this host as one.
         */
        @Test
        @DisabledOnOs(OS.WINDOWS)
        void leavesOutALinkWhateverItPointsAt() throws IOException {
            Path outside = Files.write(workspace.resolve("outside.log"), CHUNK);
            Files.createSymbolicLink(session.resolve("app.log"), outside);
            write("gc.jvm-log");

            List<String> names = storage.singleSession(SESSION_ID, true).orElseThrow().files().stream()
                    .map(RepositoryFile::name)
                    .toList();

            assertEquals(List.of("gc.jvm-log"), names);
        }

        private Instant createdAtOf(String name) {
            return storage.singleSession(SESSION_ID, true).orElseThrow().files().stream()
                    .filter(file -> file.name().equals(name))
                    .map(RepositoryFile::createdAt)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("not listed: " + name));
        }

        @Test
        void deletesAChunkByItsId() throws IOException {
            Path chunk = write("profile-20260101-120000.jfr");
            Path log = write("gc.jvm-log");

            storage.deleteRepositoryFiles(SESSION_ID, List.of(idOf("profile-20260101-120000.jfr")));

            assertFalse(Files.exists(chunk), "the chunk named by its extension-less id is gone");
            assertTrue(Files.exists(log));
        }
    }
}
