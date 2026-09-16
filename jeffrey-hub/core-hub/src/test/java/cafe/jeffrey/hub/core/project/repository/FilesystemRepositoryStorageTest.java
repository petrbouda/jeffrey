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

import cafe.jeffrey.hub.core.project.repository.file.RecordingNameFileInfoProcessor;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.ManagedFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilesystemRepositoryStorageTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    /** Long enough that LZ4 has something to do with it, short enough to read in a failure. */
    private static final String CHUNK_BODY = "a chunk of events, repeated so the frame compresses".repeat(20);

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
                    FilesystemRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, ManagedFile.JFR));
        }

        @Test
        void opensTheLogsAndCachesOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.LIVE_FILE,
                    FilesystemRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, ManagedFile.JVM_LOG));
            assertSame(FileSizeReader.LIVE_FILE,
                    FilesystemRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, ManagedFile.ASPROF_TEMP));
        }

        @Test
        void readsACompressedRecordingFromTheListingEvenWhileTheSessionRecords() {
            // This hub wrote and closed it, so no other client holds it open and the listing is
            // final. A long session accumulates one of these every chunk, and opening each would
            // grow the cost of a listing without bound.
            assertSame(FileSizeReader.FILE_ATTRIBUTES,
                    FilesystemRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, ManagedFile.JFR_LZ4));
        }

        @Test
        void readsEveryFileOfAFinishedSessionFromTheListing() {
            for (ManagedFile fileType : ManagedFile.values()) {
                assertSame(FileSizeReader.FILE_ATTRIBUTES,
                        FilesystemRepositoryStorage.sizeReader(RecordingStatus.FINISHED, fileType),
                        "file type: " + fileType);
            }
        }
    }

    @Nested
    class Describe {

        private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);

        @TempDir
        Path workspace;

        private FilesystemRepositoryStorage storage() {
            return new FilesystemRepositoryStorage(
                    mock(ProjectInfo.class),
                    workspace,
                    mock(ProjectRepositoryRepository.class),
                    new RecordingNameFileInfoProcessor());
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
            assertEquals(ManagedFile.JVM_LOG, described.fileType());
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
     * Handing one file of a session over — a chunk, a log, a dump, through the same call.
     *
     * <p>A lookup and nothing else: what the reader receives must be the file the listing named,
     * because the download carries the bytes and their length and no name at all. And a refusal
     * rather than an empty answer, because a caller that named one file and got nothing back
     * cannot tell which of five reasons applied.
     */
    @Nested
    class OneFile {

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

        private FilesystemRepositoryStorage storage(Instant finishedAt) {
            when(repository.getAll()).thenReturn(List.of(new RepositoryInfo(
                    "repo-1", RepositoryType.ASYNC_PROFILER, null, "ws", PROJECT)));
            when(repository.findSessionById(SESSION_ID)).thenReturn(Optional.of(new ProjectInstanceSessionInfo(
                    SESSION_ID, "repo-1", INSTANCE, 0, Path.of(INSTANCE, SESSION_ID),
                    T0, T0, finishedAt, false, false, null)));
            when(repository.findLatestSessionId()).thenReturn(Optional.of(SESSION_ID));

            return new FilesystemRepositoryStorage(
                    mock(ProjectInfo.class), workspacesDir, repository, new RecordingNameFileInfoProcessor());
        }

        private FilesystemRepositoryStorage finishedSession() {
            return storage(T0.plusSeconds(600));
        }

        private static Path write(Path dir, String name, String content) throws IOException {
            return Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
        }

        private static String refusal(Executable call) {
            return assertThrows(IllegalArgumentException.class, call).getMessage();
        }

        /**
         * The file is handed over as it lies. Compressing it here — which is what this did, so the
         * transfer would carry less — rewrote the repository in the middle of a read and changed
         * the file's name under a reader that had already been told the old one.
         */
        @Test
        void isHandedOverAsTheListingNamedIt() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "chunk one");

            Path served = finishedSession().file(SESSION_ID, "profile-20260220-120000");

            assertEquals("profile-20260220-120000.jfr", served.getFileName().toString());
            assertTrue(Files.exists(session.resolve("profile-20260220-120000.jfr")),
                    "reading a session must not rewrite it");
            assertFalse(Files.exists(session.resolve("profile-20260220-120000.jfr.lz4")));
        }

        /**
         * One call for every kind. What a file's category decides is what the reader does with the
         * bytes, not whether the hub hands them over — and the reader knows the category already.
         */
        @Test
        void servesAnArtifactThroughTheSameCall() throws IOException {
            Path session = sessionDir();
            write(session, "service-app.log", "a line");

            assertEquals("service-app.log",
                    finishedSession().file(SESSION_ID, "service-app.log").getFileName().toString());
        }

        @Test
        void refusesAnIdTheSessionDoesNotHold() throws IOException {
            sessionDir();

            assertTrue(refusal(() -> finishedSession().file(SESSION_ID, "profile-20260220-999999"))
                    .contains("holds no file with id"));
        }

        @Test
        void refusesTheChunkTheProfilerIsStillWriting() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "closed");
            write(session, "profile-20260220-120500.jfr", "still being written");

            assertTrue(refusal(() -> storage(null).file(SESSION_ID, "profile-20260220-120500"))
                    .contains("still writing"));
        }

        /**
         * A profiler stopped before it wrote an event leaves a zero-byte recording. It used to be
         * dropped by the compression that ran here, and a caller was told "not found".
         */
        @Test
        void refusesAnEmptyRecordingAndSaysSo() throws IOException {
            Path session = sessionDir();
            Files.createFile(session.resolve("profile-20260220-120000.jfr"));

            assertTrue(refusal(() -> finishedSession().file(SESSION_ID, "profile-20260220-120000"))
                    .contains("is empty"));
        }

        /**
         * Emptiness is a statement about parsing, so it is asked of recordings only. A log with
         * nothing in it is an answer — a reader that asked for one wants the nothing it holds,
         * not a refusal it has to interpret.
         */
        @Test
        void servesAnEmptyArtifact() throws IOException {
            Path session = sessionDir();
            Files.createFile(session.resolve("service-app.log"));

            Path served = finishedSession().file(SESSION_ID, "service-app.log");

            assertEquals("service-app.log", served.getFileName().toString());
            assertEquals(0, Files.size(served));
        }

        /**
         * async-profiler deletes its {@code .jfr.N~} cache as it goes, so it is never a file a
         * reader can be handed.
         */
        @Test
        void refusesATransientFile() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr.1~", "cache");

            assertTrue(refusal(() -> finishedSession().file(SESSION_ID, "profile-20260220-120000.jfr.1~"))
                    .contains("transient"));
        }

        /**
         * The compression job is between publishing an archive and removing the recording it was
         * made from, so one id names two files. Both are whole — an archive is renamed onto its
         * name and so is never listed half-written — and the archive is the one served, because
         * it is the one that will still be there: the recording beside it is about to go, and a
         * reader that resolved it can find it gone before it opens it.
         */
        @Test
        void prefersTheArchiveOverTheRecordingItWasMadeFrom() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "the whole chunk");
            write(session, "profile-20260220-120000.jfr.lz4", "the archive of it");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    finishedSession().file(SESSION_ID, "profile-20260220-120000").getFileName().toString());
        }

        /**
         * And the other way round, since a reduce over two entries sees them in listing order and
         * must answer the same either way.
         */
        @Test
        void prefersTheArchiveWhicheverWayTheListingOrdersThem() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr.lz4", "the archive of it");
            write(session, "profile-20260220-120000.jfr", "the whole chunk");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    finishedSession().file(SESSION_ID, "profile-20260220-120000").getFileName().toString());
        }

        @Test
        void servesTheArchiveOnceTheRecordingIsGone() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr.lz4", "the archive");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    finishedSession().file(SESSION_ID, "profile-20260220-120000").getFileName().toString());
        }
    }

    /**
     * The compression job, which is now the only thing in the tree that rewrites a repository
     * file. It had no test at all: the six that arrived with it were written against the lookup
     * that used to compress on the way out of a read, and went when that did.
     */
    @Nested
    class CompressSession {

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

        private FilesystemRepositoryStorage storage(Instant finishedAt) {
            when(repository.getAll()).thenReturn(List.of(new RepositoryInfo(
                    "repo-1", RepositoryType.ASYNC_PROFILER, null, "ws", PROJECT)));
            when(repository.findSessionById(SESSION_ID)).thenReturn(Optional.of(new ProjectInstanceSessionInfo(
                    SESSION_ID, "repo-1", INSTANCE, 0, Path.of(INSTANCE, SESSION_ID),
                    T0, T0, finishedAt, false, false, null)));
            when(repository.findLatestSessionId()).thenReturn(Optional.of(SESSION_ID));

            return new FilesystemRepositoryStorage(
                    mock(ProjectInfo.class), workspacesDir, repository, new RecordingNameFileInfoProcessor());
        }

        private FilesystemRepositoryStorage finishedSession() {
            return storage(T0.plusSeconds(1800));
        }

        private FilesystemRepositoryStorage liveSession() {
            return storage(null);
        }

        private static Path write(Path dir, String name) throws IOException {
            return Files.write(dir.resolve(name), CHUNK_BODY.getBytes(StandardCharsets.UTF_8));
        }

        private static List<String> names(Path dir) throws IOException {
            try (var entries = Files.list(dir)) {
                return entries.map(path -> path.getFileName().toString()).sorted().toList();
            }
        }

        @Test
        void replacesEveryClosedChunkWithItsArchive() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr");
            write(session, "profile-20260220-121000.jfr");

            assertEquals(2, finishedSession().compressSession(SESSION_ID));

            assertEquals(
                    List.of("profile-20260220-120000.jfr.lz4", "profile-20260220-121000.jfr.lz4"),
                    names(session),
                    "each recording is gone and its archive is there");
        }

        /**
         * Compressing the file the profiler holds open would compress a prefix of it and then
         * delete the file being written into. It is left out by {@code finishedRecordings()},
         * which is the one definition of which chunk that is.
         */
        @Test
        void leavesTheChunkTheProfilerIsStillWriting() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr");
            write(session, "profile-20260220-121000.jfr");

            assertEquals(1, liveSession().compressSession(SESSION_ID));

            assertEquals(
                    List.of("profile-20260220-120000.jfr.lz4", "profile-20260220-121000.jfr"),
                    names(session),
                    "the newest chunk of a live session is untouched");
        }

        /**
         * The live bug this rule was written for. {@code app.pprof.lz4} matches nothing in the
         * enum, so the file came back UNKNOWN, stopped being a recording, changed id and took the
         * compression's timestamp — with the original deleted. Only JFR ever round-tripped.
         */
        @Test
        void leavesARecordingItsTypeCannotCompress() throws IOException {
            Path session = sessionDir();
            write(session, "app.pprof");
            write(session, "app.otlp");

            finishedSession().compressSession(SESSION_ID);

            assertEquals(List.of("app.otlp", "app.pprof"), names(session));
        }

        /**
         * A profiler stopped before it wrote an event leaves a zero-byte recording. Compressing
         * one would delete it in favour of an archive of nothing, and the emptiness is what the
         * hub reports to a reader that asks for the file.
         */
        @Test
        void leavesAnEmptyRecordingAlone() throws IOException {
            Path session = sessionDir();
            Files.createFile(session.resolve("profile-20260220-120000.jfr"));

            assertEquals(0, finishedSession().compressSession(SESSION_ID));

            assertEquals(List.of("profile-20260220-120000.jfr"), names(session));
        }

        /**
         * A second run has nothing to do, and says so by counting the archive it finds rather
         * than compressing it again.
         */
        @Test
        void isIdempotent() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr");

            assertEquals(1, finishedSession().compressSession(SESSION_ID));
            long firstSize = Files.size(session.resolve("profile-20260220-120000.jfr.lz4"));

            assertEquals(1, finishedSession().compressSession(SESSION_ID));

            assertEquals(List.of("profile-20260220-120000.jfr.lz4"), names(session));
            assertEquals(firstSize, Files.size(session.resolve("profile-20260220-120000.jfr.lz4")));
        }

        /**
         * The recording is deleted on the strength of an archive already being there, which is
         * only safe because an archive under its own name is whole: it is renamed onto that name
         * rather than written to it. Before the rename a hub killed mid-compression left a
         * partial archive beside a whole recording, and this shortcut deleted the recording.
         */
        @Test
        void removesARecordingWhoseArchiveIsAlreadyThere() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr");
            write(session, "profile-20260220-120000.jfr.lz4");

            finishedSession().compressSession(SESSION_ID);

            assertEquals(List.of("profile-20260220-120000.jfr.lz4"), names(session));
        }

        /**
         * The scratch file a compression writes into is hidden, so a session that is listed while
         * one is in flight does not report a file that is about to stop existing — under a name
         * no reader could place, at a size that is still growing.
         */
        @Test
        void aScratchFileIsNoFileOfTheSession() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr");
            Files.write(session.resolve(".profile-20260220-120000.jfr.lz4.0e57.tmp"),
                    "half an archive".getBytes(StandardCharsets.UTF_8));

            List<RepositoryFile> files = finishedSession()
                    .singleSession(SESSION_ID, true)
                    .orElseThrow()
                    .files();

            assertEquals(
                    List.of("profile-20260220-120000.jfr"),
                    files.stream().map(RepositoryFile::name).toList());
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

        private FilesystemRepositoryStorage storage() {
            when(repository.getAll()).thenReturn(List.of(new RepositoryInfo(
                    "repo-1", RepositoryType.ASYNC_PROFILER, null, "ws", PROJECT)));
            when(repository.findSessionById(SESSION_ID)).thenReturn(Optional.of(new ProjectInstanceSessionInfo(
                    SESSION_ID, "repo-1", INSTANCE, 0, Path.of(INSTANCE, SESSION_ID),
                    T0, T0, null, false, false, null)));

            return new FilesystemRepositoryStorage(
                    mock(ProjectInfo.class), workspacesDir, repository, new RecordingNameFileInfoProcessor());
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
