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

package cafe.jeffrey.volumescheduler.volume;

import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import cafe.jeffrey.shared.test.volume.VolumeWriter;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One project as the jobs read and change it. How the layout itself reads the volume — which
 * directory is a session, the order of sessions, which one is newest — is pinned once, in
 * {@code shared/volume}'s {@code ProjectTreeTest}, {@code ProjectSessionsTest} and
 * {@code VolumeChangesTest}; this pins what the volume scheduler builds on it.
 */
class VolumeProjectTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final ProjectRef REF = new ProjectRef("ws", "project");
    private static final String INSTANCE = "pod-a";
    private static final String SESSION_ID = VolumeWriter.sessionIdAt(T0);

    @TempDir
    Path workspacesDir;

    private Path projectDir;
    private VolumeProject project;

    @BeforeEach
    void setUp() {
        projectDir = VolumeWriter.project(workspacesDir.resolve(REF.workspaceId()), REF.projectId());
        project = ProjectVolumeFixture.project(REF, workspacesDir, projectDir);
    }

    /** A session started at {@code start} in the instance: its directory. */
    private Path session(String instanceId, Instant start) {
        return VolumeWriter.session(VolumeWriter.instance(projectDir, instanceId), start);
    }

    private RecordingSession listed(String sessionId) {
        return project.sessions().stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst()
                .orElseThrow();
    }

    private static Path write(Path dir, String name) throws IOException {
        return Files.write(dir.resolve(name), "x".getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    class Listing {

        /** What the compression job works on: each instance's two newest sessions, read with their files. */
        @Test
        void theNewestSessionsAreEachInstancesNewestTwo() {
            session(INSTANCE, T0);
            String a2 = session(INSTANCE, T0.plusSeconds(60)).getFileName().toString();
            String a3 = session(INSTANCE, T0.plusSeconds(120)).getFileName().toString();
            String b1 = session("pod-b", T0.plusSeconds(30)).getFileName().toString();

            Map<String, Boolean> newestById = project.newestSessions(2).stream()
                    .collect(Collectors.toMap(RecordingSession::id, RecordingSession::newest));

            assertEquals(Map.of(a3, true, a2, false, b1, true), newestById);
        }

        @Test
        void describesEachFileFromTheListing() throws IOException {
            Path session = session(INSTANCE, T0);
            Path chunk = write(session, "rec-20260220-120500+0000.jfr");
            write(session, "service-app.log");

            List<RecordingSessionFile> files = listed(SESSION_ID).files();

            RecordingSessionFile recording = files.stream()
                    .filter(RecordingSessionFile::recording)
                    .findFirst()
                    .orElseThrow();
            assertEquals("rec-20260220-120500+0000", recording.id());
            assertEquals("rec-20260220-120500+0000.jfr", recording.name());
            assertEquals(1L, recording.size());
            assertEquals(chunk.toAbsolutePath(), recording.path().toAbsolutePath());
            assertEquals(List.of("service-app.log"), files.stream()
                    .filter(file -> !file.recording())
                    .map(RecordingSessionFile::id)
                    .toList(), "an artifact is listed under its whole name");
        }

        /**
         * A chunk's name carries the zone its machine runs in, and is read in it: a JVM in Prague
         * names the chunk it opened at 12:05 UTC {@code 13:05}.
         */
        @Test
        void readsItsRecordingsNamesInTheZoneTheyCarry() throws IOException {
            write(session(INSTANCE, T0), "rec-20260220-130500+0100.jfr");

            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), listed(SESSION_ID).files().getFirst().createdAt());
        }

        /** A name without a zone — a custom template — is read in UTC. */
        @Test
        void readsANameWithoutAZoneInUtc() throws IOException {
            write(session(INSTANCE, T0), "rec-20260220-120500.jfr");

            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), listed(SESSION_ID).files().getFirst().createdAt());
        }

        /**
         * The scratch file a compression writes into is hidden, so a session that is listed while
         * one is in flight does not report a file that is about to stop existing — under a name
         * no reader could place, at a size that is still growing.
         */
        @Test
        void aScratchFileIsNoFileOfTheSession() throws IOException {
            Path session = session(INSTANCE, T0);
            write(session, "rec-20260220-120000+0000.jfr");
            write(session, ".rec-20260220-120000+0000.jfr.lz4.0e57.tmp");

            assertEquals(List.of("rec-20260220-120000+0000.jfr"),
                    listed(SESSION_ID).files().stream().map(RecordingSessionFile::name).toList());
        }

        @Test
        void anInstanceWithoutASessionCountsNone() {
            VolumeWriter.instance(projectDir, INSTANCE);

            ProjectInstance instance = project.instances().getFirst();
            assertEquals(INSTANCE, instance.id());
            assertEquals(0, instance.sessionCount());
            assertTrue(instance.createdAt().isPresent(), "a local mount lists its directories' times");
        }

        @Test
        void anInstanceCountsItsSessions() {
            session(INSTANCE, T0);
            session(INSTANCE, T0.plusSeconds(120));

            assertEquals(2, project.instances().getFirst().sessionCount());
        }
    }

    @Nested
    class DeleteSession {

        /**
         * Pins and liveness files from before they were dropped are hidden entries like any other:
         * they protect nothing, and the session goes with them.
         */
        @Test
        void aLeftoverPinOrLivenessFileDoesNotKeepTheSession() throws IOException {
            Path older = session(INSTANCE, T0);
            session(INSTANCE, T0.plusSeconds(600));
            Files.createFile(older.resolve(".retained"));
            Files.createDirectories(older.resolve(".heartbeat"));
            Files.writeString(older.resolve(".heartbeat").resolve("finished"), "0");

            assertEquals(SessionDeletion.SESSION_DELETED, project.deleteSession(SESSION_ID));

            assertFalse(Files.exists(older));
        }
    }

    /**
     * Deleting the files a retention rule named by the ids the listing gave them. An id that does
     * not find its file frees nothing.
     */
    @Nested
    class DeleteFiles {

        /**
         * The id is the name with the recording extension stripped, so it cannot be turned back
         * into a path by appending it to the session directory — which is what this once did, and
         * why it deleted nothing at all.
         */
        @Test
        void deletesAChunkNamedByItsId() throws IOException {
            Path session = session(INSTANCE, T0);
            Path chunk = write(session, "rec-20260220-120000+0000.jfr");
            Path kept = write(session, "rec-20260220-120500+0000.jfr");

            project.deleteFiles(SESSION_ID, List.of("rec-20260220-120000+0000"));

            assertFalse(Files.exists(chunk));
            assertTrue(Files.exists(kept), "only the file that was named goes");
        }

        /**
         * The id survives the file being compressed — that is what stripping the extension is for —
         * so an id taken from a listing before the compression still names the chunk after.
         */
        @Test
        void deletesTheCompressedFormUnderTheSameId() throws IOException {
            Path archive = write(session(INSTANCE, T0), "rec-20260220-120000+0000.jfr.lz4");

            project.deleteFiles(SESSION_ID, List.of("rec-20260220-120000+0000"));

            assertFalse(Files.exists(archive));
        }

        @Test
        void deletesAnArtifactNamedByItsId() throws IOException {
            Path log = write(session(INSTANCE, T0), "service-app.log");

            project.deleteFiles(SESSION_ID, List.of("service-app.log"));

            assertFalse(Files.exists(log));
        }

        @Test
        void leavesTheSessionAloneForAnIdItDoesNotHold() throws IOException {
            Path chunk = write(session(INSTANCE, T0), "rec-20260220-120000+0000.jfr");

            project.deleteFiles(SESSION_ID, List.of("rec-20260220-999999+0000"));

            assertTrue(Files.exists(chunk));
        }

        /**
         * An id is only a name. Resolving one against the session directory let a {@code ../}
         * walk out of it, and every file the service could delete was one it would delete.
         * Matching against the directory's own entries leaves nothing to resolve.
         */
        @Test
        void cannotReachAFileOutsideTheSessionDirectory() throws IOException {
            Path session = session(INSTANCE, T0);
            Path outside = write(workspacesDir, "keep-me.txt");

            // Exactly the depth the old resolve() needed: the session sits four levels under the
            // workspaces directory, so this id named that file and deleteIfExists removed it.
            assertEquals(workspacesDir, session.resolve("../../../..").normalize());
            project.deleteFiles(SESSION_ID, List.of("../../../../keep-me.txt"));

            assertTrue(Files.exists(outside), "an id is matched against the session's files, not resolved to a path");
        }
    }

    /**
     * The stale-instance rule's delete: the instance goes with everything in it, so it is looked at
     * once more first, and a session found there keeps it.
     */
    @Nested
    class DeleteInstance {

        @Test
        void deletesAnInstanceThatHoldsNoSession() throws IOException {
            Path instance = VolumeWriter.instance(projectDir, "abandoned");
            Files.createDirectories(instance.resolve("not-a-session"));

            assertTrue(project.deleteInstance("abandoned"));
            assertFalse(Files.exists(instance));
        }

        /** A session laid down after the rule listed the instance as empty keeps it. */
        @Test
        void keepsAnInstanceThatHoldsASession() {
            Path session = session(INSTANCE, T0);

            assertFalse(project.deleteInstance(INSTANCE));
            assertTrue(Files.isDirectory(session));
        }

        @Test
        void deletesNothingForAnInstanceThatIsNotThere() {
            assertFalse(project.deleteInstance("missing"));
        }
    }
}
