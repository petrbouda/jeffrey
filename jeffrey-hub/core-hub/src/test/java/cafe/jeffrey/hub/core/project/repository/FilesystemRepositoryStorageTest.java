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
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemRepositoryStorageTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final String SESSION_ID = TestVolume.sessionId(T0);
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo("project-1", "project", "ws", Optional.of(T0));

    private static Path projectDir(Path workspacesDir) {
        return TestVolume.project(workspacesDir.resolve("ws"), PROJECT_INFO.name());
    }

    private static Path sessionDir(Path workspacesDir) {
        return TestVolume.session(TestVolume.instance(projectDir(workspacesDir), "instance"), T0);
    }

    /**
     * The project with its one session: its instance's newest unless {@code ended}, when a newer
     * session in the same instance has started after it.
     */
    private static FilesystemRepositoryStorage storage(Path workspacesDir, boolean ended) {
        sessionDir(workspacesDir);
        if (ended) {
            TestVolume.session(TestVolume.instance(projectDir(workspacesDir), "instance"), T0.plusSeconds(1800));
        }
        return TestVolume.storage(PROJECT_INFO, workspacesDir, projectDir(workspacesDir));
    }

    /** One session as the retention pass and the compression job read it: from the listing, with its files. */
    private static Optional<RecordingSession> listed(RepositoryStorage storage, String sessionId) {
        return storage.listSessions(SessionDetail.WITH_FILES).stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst();
    }

    /** Compresses the one session as the compression job reads it: listed, with its files. */
    private static int compress(RepositoryStorage storage) {
        return storage.compressSession(listed(storage, SESSION_ID).orElseThrow());
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
     * their directories name them, and their order says which session is each instance's newest.
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
            storage = TestVolume.storage(PROJECT_INFO, workspacesDir, project);
        }

        /** A session started at {@code start} in the instance; its id. */
        private String session(String instanceId, Instant start) {
            return TestVolume.session(TestVolume.instance(project, instanceId), start).getFileName().toString();
        }

        /** What the compression job works on: each instance's two newest sessions, read with their files. */
        @Test
        void theNewestSessionsAreEachInstancesNewestTwo() {
            session(INSTANCE, T0);
            String a2 = session(INSTANCE, T0.plusSeconds(60));
            String a3 = session(INSTANCE, T0.plusSeconds(120));
            String b1 = session("pod-b", T0.plusSeconds(30));

            Map<String, Boolean> newestById = storage.newestSessions(2).stream()
                    .collect(Collectors.toMap(RecordingSession::id, RecordingSession::newest));

            assertEquals(Map.of(a3, true, a2, false, b1, true), newestById);
        }

        @Test
        void listsEverySessionNewestFirst() {
            String old = session(INSTANCE, T0);
            String recent = session("pod-b", T0.plusSeconds(120));

            List<RecordingSession> sessions = storage.listSessions(SessionDetail.HEADERS);

            assertEquals(List.of(recent, old), sessions.stream().map(RecordingSession::id).toList());
            assertEquals(List.of(true, true), sessions.stream().map(RecordingSession::newest).toList(),
                    "each is its own instance's newest");
            assertEquals("pod-b/" + recent, sessions.getFirst().name());
        }

        /**
         * A chunk's name carries the zone its machine runs in, and is read in it: a JVM in Prague
         * names the chunk it opened at 12:05 UTC {@code 13:05}.
         */
        @Test
        void readsItsRecordingsNamesInTheZoneTheyCarry() throws IOException {
            String id = session(INSTANCE, T0);
            Files.writeString(project.resolve(INSTANCE).resolve(id).resolve("rec-20260220-130500+0100.jfr"),
                    CHUNK_BODY);

            RecordingSession session = listed(storage, id).orElseThrow();

            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), session.files().getFirst().createdAt());
        }

        /** A name without a zone — a custom template — is read in UTC. */
        @Test
        void readsANameWithoutAZoneInUtc() throws IOException {
            String id = session(INSTANCE, T0);
            Files.writeString(project.resolve(INSTANCE).resolve(id).resolve("rec-20260220-120500.jfr"), CHUNK_BODY);

            RecordingSession session = listed(storage, id).orElseThrow();

            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), session.files().getFirst().createdAt());
        }

        @Test
        void aNewerSessionInTheSameInstanceIsTheNewest() {
            String first = session(INSTANCE, T0);
            String second = session(INSTANCE, T0.plusSeconds(600));

            assertFalse(listed(storage, first).orElseThrow().newest());
            assertTrue(listed(storage, second).orElseThrow().newest());
        }

        /** A session is a directory named for its start; anything else in an instance is not one. */
        @Test
        void aDirectoryNotNamedForItsStartIsNoSession() throws IOException {
            String id = session(INSTANCE, T0);
            Files.createDirectories(project.resolve(INSTANCE).resolve("half-written"));

            assertEquals(List.of(id), storage.listSessions(SessionDetail.HEADERS).stream()
                    .map(RecordingSession::id).toList());
        }

        @Nested
        class Instances {

            @Test
            void withoutASessionHasNone() {
                TestVolume.instance(project, INSTANCE);

                ProjectInstanceInfo instance = storage.instances().getFirst();
                assertEquals(INSTANCE, instance.id());
                assertEquals(0, instance.sessionCount());
                assertTrue(instance.startedAt().isPresent(), "its directory's time, as the mount gives it");
            }

            @Test
            void countsItsSessions() {
                session(INSTANCE, T0);
                session(INSTANCE, T0.plusSeconds(120));

                assertEquals(2, storage.instances().getFirst().sessionCount());
            }
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

        /** The session a newer one has ended: every recording of it is complete. */
        private FilesystemRepositoryStorage endedSession() {
            return FilesystemRepositoryStorageTest.storage(workspacesDir, true);
        }

        /** The instance's newest session, whose newest recording may still be on its way. */
        private FilesystemRepositoryStorage newestSession() {
            return FilesystemRepositoryStorageTest.storage(workspacesDir, false);
        }

        private static Path write(Path dir, String name) throws IOException {
            return Files.write(dir.resolve(name), CHUNK_BODY.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void replacesEveryClosedChunkWithItsArchive() throws IOException {
            Path session = sessionDir();
            write(session, "rec-20260220-120000.jfr");
            write(session, "rec-20260220-121000.jfr");

            assertEquals(2, compress(endedSession()));

            assertEquals(
                    List.of("rec-20260220-120000.jfr.lz4", "rec-20260220-121000.jfr.lz4"),
                    names(session),
                    "each recording is gone and its archive is there");
        }

        /**
         * Compressing a recording still being copied into place would compress a prefix of it and
         * then delete the file being written into. It is left out by {@code trimmableRecordings()},
         * which is the one definition of which recording that may be.
         */
        @Test
        void leavesTheNewestRecordingOfTheNewestSession() throws IOException {
            Path session = sessionDir();
            write(session, "rec-20260220-120000.jfr");
            write(session, "rec-20260220-121000.jfr");

            assertEquals(1, compress(newestSession()));

            assertEquals(
                    List.of("rec-20260220-120000.jfr.lz4", "rec-20260220-121000.jfr"),
                    names(session),
                    "the newest recording of the instance's newest session is untouched");
        }

        /**
         * Two instances of one project record at once, so only one of the two recording sessions
         * is the project's newest — and that must change nothing. Which session is the newest is a
         * fact of its own instance, not of its place in the project's listing. The older live
         * session once read as FINISHED and had the chunk its profiler was writing compressed.
         */
        @Test
        void everyInstancesNewestSessionKeepsItsNewestRecordingWhateverItsPosition() throws IOException {
            Path older = sessionDir();
            write(older, "rec-20260220-120000.jfr");
            write(older, "rec-20260220-121000.jfr");
            FilesystemRepositoryStorage storage = newestSession();
            Path newer = TestVolume.session(
                    TestVolume.instance(projectDir(workspacesDir), "instance-2"), T0.plusSeconds(1800));
            write(newer, "rec-20260220-123000.jfr");

            List<RecordingSession> sessions = storage.listSessions(SessionDetail.WITH_FILES);

            assertEquals(List.of(true, true), sessions.stream().map(RecordingSession::newest).toList());
            assertEquals(List.of("rec-20260220-123000.jfr", "rec-20260220-121000.jfr"),
                    sessions.stream().map(session -> session.openRecording().orElseThrow().name()).toList(),
                    "each instance's newest session holds back its own newest recording");
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

            compress(endedSession());

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
            Files.createFile(session.resolve("rec-20260220-120000.jfr"));

            assertEquals(0, compress(endedSession()));

            assertEquals(List.of("rec-20260220-120000.jfr"), names(session));
        }

        /**
         * A second run has nothing to do, and says so by counting the archive it finds rather
         * than compressing it again.
         */
        @Test
        void isIdempotent() throws IOException {
            Path session = sessionDir();
            write(session, "rec-20260220-120000.jfr");

            assertEquals(1, compress(endedSession()));
            long firstSize = Files.size(session.resolve("rec-20260220-120000.jfr.lz4"));

            assertEquals(1, compress(endedSession()));

            assertEquals(List.of("rec-20260220-120000.jfr.lz4"), names(session));
            assertEquals(firstSize, Files.size(session.resolve("rec-20260220-120000.jfr.lz4")));
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
            write(session, "rec-20260220-120000.jfr");
            write(session, "rec-20260220-120000.jfr.lz4");

            compress(endedSession());

            assertEquals(List.of("rec-20260220-120000.jfr.lz4"), names(session));
        }

        /**
         * The scratch file a compression writes into is hidden, so a session that is listed while
         * one is in flight does not report a file that is about to stop existing — under a name
         * no reader could place, at a size that is still growing.
         */
        @Test
        void aScratchFileIsNoFileOfTheSession() throws IOException {
            Path session = sessionDir();
            write(session, "rec-20260220-120000.jfr");
            Files.write(session.resolve(".rec-20260220-120000.jfr.lz4.0e57.tmp"),
                    "half an archive".getBytes(StandardCharsets.UTF_8));

            List<RepositoryFile> files = listed(endedSession(), SESSION_ID)
                    .orElseThrow()
                    .files();

            assertEquals(
                    List.of("rec-20260220-120000.jfr"),
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
            return FilesystemRepositoryStorageTest.storage(workspacesDir, false);
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
            Path chunk = write(session, "rec-20260220-120000.jfr");
            Path kept = write(session, "rec-20260220-120500.jfr");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("rec-20260220-120000"));

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
            Path archive = write(session, "rec-20260220-120000.jfr.lz4");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("rec-20260220-120000"));

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
            Path chunk = write(session, "rec-20260220-120000.jfr");

            storage().deleteRepositoryFiles(SESSION_ID, List.of("rec-20260220-999999"));

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
