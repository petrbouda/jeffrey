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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.JeffreyLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemRepositoryStorageTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final String SESSION_ID = "session-1";
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo("project-1", "project", "ws", T0, Map.of());

    private static Path projectDir(Path workspacesDir) {
        return TestVolume.project(workspacesDir.resolve("ws"), PROJECT_INFO.id(), PROJECT_INFO.name(), T0);
    }

    private static Path sessionDir(Path workspacesDir) {
        return TestVolume.session(TestVolume.instance(projectDir(workspacesDir), "instance", T0), SESSION_ID, T0);
    }

    /**
     * The project with its one session, still recording (a heartbeat fresh at {@link #NOW}) when
     * {@code finishedAt} is null, finished by its clean-exit marker otherwise.
     */
    private static FilesystemRepositoryStorage storage(Path workspacesDir, Instant finishedAt) {
        Path session = sessionDir(workspacesDir);
        if (finishedAt == null) {
            TestVolume.heartbeat(session, NOW);
        } else {
            TestVolume.finished(session, finishedAt);
        }
        return new FilesystemRepositoryStorage(
                PROJECT_INFO, projectDir(workspacesDir), TestVolume.liveness(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    /** The session directory's files as a reader sees them: dot-files are the volume's own bookkeeping. */
    private static List<String> names(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }

    /** Long enough that LZ4 has something to do with it, short enough to read in a failure. */
    private static final String CHUNK_BODY = "a chunk of events, repeated so the frame compresses".repeat(20);

    /**
     * What the volume says about a project's sessions and instances, read at the moment of asking:
     * markers name them, heartbeat files say whether they are still recording, and a
     * {@code .retained} file pins one.
     */
    @Nested
    class Listing {

        private static final String INSTANCE = "pod-a";

        @TempDir
        Path workspacesDir;

        private Path project;
        private FilesystemRepositoryStorage storage;

        @BeforeEach
        void setUp() {
            project = projectDir(workspacesDir);
            storage = new FilesystemRepositoryStorage(
                    PROJECT_INFO, project, TestVolume.liveness(Clock.fixed(NOW, ZoneOffset.UTC)));
        }

        private Path session(String instanceId, String sessionId, Instant createdAt) {
            return TestVolume.session(TestVolume.instance(project, instanceId, T0), sessionId, createdAt);
        }

        @Test
        void listsEverySessionNewestFirstByItsMarker() {
            TestVolume.finished(session(INSTANCE, "s-old", T0), T0.plusSeconds(60));
            TestVolume.heartbeat(session("pod-b", "s-new", T0.plusSeconds(120)), NOW);

            List<RecordingSession> sessions = storage.listSessions(SessionDetail.HEADERS);

            assertEquals(List.of("s-new", "s-old"), sessions.stream().map(RecordingSession::id).toList());
            assertEquals(List.of(RecordingStatus.ACTIVE, RecordingStatus.FINISHED),
                    sessions.stream().map(RecordingSession::status).toList());
            assertEquals("pod-b/s-new", sessions.getFirst().name());
        }

        /** A newer session in the same instance means the older one ended, even with a fresh beat. */
        @Test
        void aNewerSessionOfTheSameInstanceFinishesTheOlderOne() {
            Path older = session(INSTANCE, "s-1", T0);
            TestVolume.heartbeat(older, NOW);
            TestVolume.heartbeat(session(INSTANCE, "s-2", T0.plusSeconds(600)), NOW);

            List<RecordingSession> sessions = storage.instanceWithSessions(INSTANCE).orElseThrow().sessions();

            assertEquals(List.of(RecordingStatus.ACTIVE, RecordingStatus.FINISHED),
                    sessions.stream().map(RecordingSession::status).toList());
            assertEquals(NOW, sessions.get(1).finishedAt());
        }

        @Test
        void anInstanceTakesItsStatusAndFinishFromItsNewestSession() {
            TestVolume.finished(session(INSTANCE, "s-1", T0), T0.plusSeconds(60));
            TestVolume.finished(session(INSTANCE, "s-2", T0.plusSeconds(120)), T0.plusSeconds(300));

            ProjectInstanceInfo instance = storage.instanceWithSessions(INSTANCE).orElseThrow().instance();

            assertEquals(ProjectInstanceStatus.FINISHED, instance.status());
            assertEquals(T0.plusSeconds(300), instance.finishedAt());
            assertEquals(2, instance.sessionCount());
        }

        @Test
        void anInstanceWithoutSessionsIsPending() {
            TestVolume.instance(project, INSTANCE, T0);

            ProjectInstanceInfo instance = storage.instanceWithSessions(INSTANCE).orElseThrow().instance();

            assertEquals(ProjectInstanceStatus.PENDING, instance.status());
            assertEquals(0, instance.sessionCount());
        }

        @Test
        void anUnknownInstanceIsEmpty() {
            assertTrue(storage.instanceWithSessions("ghost").isEmpty());
            assertTrue(storage.instanceWithSessions("../escape").isEmpty());
        }

        /** One test of "is this an instance": a marker that does not read names no instance anywhere. */
        @Test
        void anInstanceWithACorruptMarkerIsNoInstance() throws IOException {
            TestVolume.heartbeat(session(INSTANCE, "s-1", T0), NOW);
            Files.writeString(project.resolve(INSTANCE).resolve(JeffreyLayout.INSTANCE_INFO_FILE), "{not json");

            assertTrue(storage.instances().isEmpty());
            assertTrue(storage.instanceWithSessions(INSTANCE).isEmpty());
            assertTrue(storage.listSessions(SessionDetail.HEADERS).isEmpty());
            assertTrue(storage.session("s-1").isEmpty());
        }

        @Test
        void aSessionReadAloneFinishesAtItsSuccessorsStartLikeItDoesInTheListing() {
            session(INSTANCE, "s-1", T0);
            TestVolume.heartbeat(session(INSTANCE, "s-2", T0.plusSeconds(600)), NOW);

            assertEquals(T0.plusSeconds(600), storage.session("s-1").orElseThrow().finishedAt());
            assertEquals(RecordingStatus.ACTIVE, storage.session("s-2").orElseThrow().status());
        }

        @Test
        void aDirectoryWithoutItsMarkerIsNoSession() throws IOException {
            TestVolume.heartbeat(session(INSTANCE, "s-1", T0), NOW);
            Files.createDirectories(project.resolve(INSTANCE).resolve("half-written"));

            assertEquals(List.of("s-1"), storage.listSessions(SessionDetail.HEADERS).stream()
                    .map(RecordingSession::id).toList());
        }

        @Test
        void anInstanceIdThatIsNotOneDirectoryNamesNothing() {
            TestVolume.heartbeat(session(INSTANCE, "s-1", T0), NOW);

            assertTrue(storage.instanceWithSessions("../" + INSTANCE).isEmpty());
            assertTrue(storage.session("../s-1").isEmpty());
        }

        @Test
        void theRetainedFlagIsAFileInTheSession() {
            Path dir = session(INSTANCE, "s-1", T0);
            TestVolume.finished(dir, T0.plusSeconds(60));

            assertTrue(storage.setRetained("s-1", true));
            assertTrue(Files.exists(dir.resolve(FilesystemRepositoryStorage.RETAINED_MARKER)));
            assertTrue(storage.session("s-1").orElseThrow().retained());
            assertTrue(storage.session("s-1").orElseThrow().files().isEmpty(), "the flag is no file of the session");

            storage.setRetained("s-1", true);
            assertTrue(storage.session("s-1").orElseThrow().retained(), "setting it again is no error");

            storage.setRetained("s-1", false);
            assertFalse(storage.session("s-1").orElseThrow().retained());
        }

        @Nested
        class Instances {

            @Test
            void withoutASessionIsPending() {
                TestVolume.instance(project, INSTANCE, T0);

                assertEquals(new ProjectInstanceInfo(
                                INSTANCE, ProjectInstanceStatus.PENDING,
                                T0, null, 0),
                        storage.instances().getFirst());
            }

            @Test
            void isActiveWhileASessionRecordsAndFinishedAtItsLastFinishAfterwards() {
                TestVolume.finished(session(INSTANCE, "s-1", T0), T0.plusSeconds(60));
                Path live = session(INSTANCE, "s-2", T0.plusSeconds(120));
                TestVolume.heartbeat(live, NOW);

                assertEquals(ProjectInstanceStatus.ACTIVE,
                        storage.instances().getFirst().status());

                TestVolume.finished(live, NOW.minusSeconds(5));

                var instance = storage.instances().getFirst();
                assertEquals(ProjectInstanceStatus.FINISHED, instance.status());
                assertEquals(NOW.minusSeconds(5), instance.finishedAt());
                assertEquals(2, instance.sessionCount());
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
                    PROJECT_INFO, workspace, TestVolume.liveness(Clock.fixed(NOW, ZoneOffset.UTC)));
        }

        private Path sessionDir() throws IOException {
            return Files.createDirectories(workspace.resolve("project/instance/session"));
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Path session = sessionDir();
            Path file = Files.write(session.resolve("gc.jvm-log"), CONTENT);

            RepositoryFile described = storage().describe(file, session);

            assertNotNull(described);
            assertEquals("gc.jvm-log", described.name());
            assertEquals(CONTENT.length, described.size());
            assertFalse(described.isRecordingFile());
        }

        @Test
        void leavesOutAFileThatWentAwayWhileTheSessionWasListed() throws IOException {
            // async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
            // listing has just read can be gone before its size is asked for. The next listing
            // will not mention it either; failing here would take the instance page down with it.
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr.1~");

            assertNull(storage().describe(vanished, session));
        }

        @Test
        void leavesOutAVanishedFileOfAFinishedSessionToo() throws IOException {
            Path session = sessionDir();
            Path vanished = session.resolve("profile-20260912-121559.jfr");

            assertNull(storage().describe(vanished, session));
        }
    }

    /**
     * Looking up one file of a session for download — a chunk, a log, a dump, through the same
     * call. A lookup only: it never rewrites the file, and each refusal is named.
     */
    @Nested
    class OneFile {

        @TempDir
        Path workspacesDir;

        private Path sessionDir() {
            return FilesystemRepositoryStorageTest.sessionDir(workspacesDir);
        }

        private FilesystemRepositoryStorage storage(Instant finishedAt) {
            return FilesystemRepositoryStorageTest.storage(workspacesDir, finishedAt);
        }

        private FilesystemRepositoryStorage finishedSession() {
            return storage(T0.plusSeconds(600));
        }

        private static Path write(Path dir, String name, String content) throws IOException {
            return Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
        }

        private static Path served(FileLookup lookup) {
            return assertInstanceOf(FileLookup.Found.class, lookup).path();
        }

        /** The file is handed over as it lies; a read path never compresses. */
        @Test
        void isHandedOverAsTheListingNamedIt() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "chunk one");

            Path served = served(finishedSession().file(SESSION_ID, "profile-20260220-120000"));

            assertEquals("profile-20260220-120000.jfr", served.getFileName().toString());
            assertTrue(Files.exists(session.resolve("profile-20260220-120000.jfr")),
                    "reading a session must not rewrite it");
            assertFalse(Files.exists(session.resolve("profile-20260220-120000.jfr.lz4")));
        }

        /** One call for every kind of file. */
        @Test
        void servesAnArtifactThroughTheSameCall() throws IOException {
            Path session = sessionDir();
            write(session, "service-app.log", "a line");

            assertEquals("service-app.log",
                    served(finishedSession().file(SESSION_ID, "service-app.log")).getFileName().toString());
        }

        @Test
        void refusesAnIdTheSessionDoesNotHold() throws IOException {
            sessionDir();

            FileLookup lookup = finishedSession().file(SESSION_ID, "profile-20260220-999999");

            assertEquals(new FileLookup.NotInSession(SESSION_ID, "profile-20260220-999999"), lookup);
            assertTrue(assertInstanceOf(FileLookup.Refused.class, lookup).reason().contains("holds no file with id"));
        }

        @Test
        void refusesTheChunkTheProfilerIsStillWriting() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "closed");
            write(session, "profile-20260220-120500.jfr", "still being written");

            assertEquals(new FileLookup.StillWriting("profile-20260220-120500.jfr"),
                    storage(null).file(SESSION_ID, "profile-20260220-120500"));
        }

        /** A profiler stopped before it wrote an event leaves a zero-byte recording. */
        @Test
        void refusesAnEmptyRecordingAndSaysSo() throws IOException {
            Path session = sessionDir();
            Files.createFile(session.resolve("profile-20260220-120000.jfr"));

            assertEquals(new FileLookup.EmptyRecording("profile-20260220-120000.jfr"),
                    finishedSession().file(SESSION_ID, "profile-20260220-120000"));
        }

        /** Emptiness is refused for recordings only; an empty log is still an answer. */
        @Test
        void servesAnEmptyArtifact() throws IOException {
            Path session = sessionDir();
            Files.createFile(session.resolve("service-app.log"));

            Path served = served(finishedSession().file(SESSION_ID, "service-app.log"));

            assertEquals("service-app.log", served.getFileName().toString());
            assertEquals(0, Files.size(served));
        }

        /** The profiler's {@code .jfr.N~} cache is served like any file; Microscope declines to ask for it. */
        @Test
        void servesTheProfilersCacheFileLikeAnyOther() throws IOException {
            Path session = sessionDir();
            Path cache = write(session, "profile-20260220-120000.jfr.1~", "cache");

            assertEquals(cache, served(finishedSession().file(SESSION_ID, "profile-20260220-120000.jfr.1~")));
        }

        /**
         * Mid-compression one id names a recording and its archive; the archive is served because it
         * is the one that stays.
         */
        @Test
        void prefersTheArchiveOverTheRecordingItWasMadeFrom() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr", "the whole chunk");
            write(session, "profile-20260220-120000.jfr.lz4", "the archive of it");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    served(finishedSession().file(SESSION_ID, "profile-20260220-120000")).getFileName().toString());
        }

        /** Whichever order the listing gives them in. */
        @Test
        void prefersTheArchiveWhicheverWayTheListingOrdersThem() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr.lz4", "the archive of it");
            write(session, "profile-20260220-120000.jfr", "the whole chunk");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    served(finishedSession().file(SESSION_ID, "profile-20260220-120000")).getFileName().toString());
        }

        @Test
        void servesTheArchiveOnceTheRecordingIsGone() throws IOException {
            Path session = sessionDir();
            write(session, "profile-20260220-120000.jfr.lz4", "the archive");

            assertEquals("profile-20260220-120000.jfr.lz4",
                    served(finishedSession().file(SESSION_ID, "profile-20260220-120000")).getFileName().toString());
        }
    }

    /** The compression job, the only thing that rewrites a repository file. */
    @Nested
    class CompressSession {

        @TempDir
        Path workspacesDir;

        private Path sessionDir() {
            return FilesystemRepositoryStorageTest.sessionDir(workspacesDir);
        }

        private FilesystemRepositoryStorage storage(Instant finishedAt) {
            return FilesystemRepositoryStorageTest.storage(workspacesDir, finishedAt);
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
         * Two instances of one project record at once, so only one of the two live sessions is
         * the project's newest — and that must change nothing. Whether a session still holds a
         * chunk open is a fact of the session (its own heartbeat), not of its place in the
         * project's listing.
         */
        @Test
        void everyUnfinishedSessionIsLiveWhateverItsPosition() throws IOException {
            Path older = sessionDir();
            write(older, "profile-20260220-120000.jfr");
            write(older, "profile-20260220-121000.jfr");
            FilesystemRepositoryStorage storage = liveSession();
            Path newer = TestVolume.session(
                    TestVolume.instance(projectDir(workspacesDir), "instance-2", T0), "session-2", T0.plusSeconds(1800));
            TestVolume.heartbeat(newer, NOW);
            write(newer, "profile-20260220-123000.jfr");

            List<RecordingSession> sessions = storage.listSessions(SessionDetail.WITH_FILES);

            assertEquals(List.of(RecordingStatus.ACTIVE, RecordingStatus.ACTIVE),
                    sessions.stream().map(RecordingSession::status).toList());
            assertEquals(List.of("profile-20260220-123000.jfr", "profile-20260220-121000.jfr"),
                    sessions.stream().map(session -> session.openRecording().orElseThrow().name()).toList(),
                    "each live session holds its own newest chunk open");
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
                    .session(SESSION_ID)
                    .orElseThrow()
                    .files();

            assertEquals(
                    List.of("profile-20260220-120000.jfr"),
                    files.stream().map(RepositoryFile::name).toList());
        }
    }

    /**
     * Deleting the files a retention job named by the ids the listing gave them. An id that does
     * not find its file frees nothing.
     */
    @Nested
    class DeleteRepositoryFiles {

        @TempDir
        Path workspacesDir;

        private Path sessionDir() {
            return FilesystemRepositoryStorageTest.sessionDir(workspacesDir);
        }

        private FilesystemRepositoryStorage storage() {
            return FilesystemRepositoryStorageTest.storage(workspacesDir, null);
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
