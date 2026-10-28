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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.dto.InstanceWithSessionsResponse;
import cafe.jeffrey.microscope.remote.client.InstancePage;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionHeaderResponse;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recording volume reached straight on its storage answers the remote clients the way a hub would:
 * the same tree, the same newest and failed sessions, the same refusals, the same changes — or none at all
 * when it is declared read-only.
 */
class VolumeRemoteClientsTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final String DESCRIPTION = "test-volume";
    private static final ProjectScope ORDERS = new ProjectScope("prod", "orders");
    /** The instance's newest session, started ten minutes in. */
    private static final Instant LIVE_START = T0.plusSeconds(600);
    private static final String LIVE = VolumeWriter.sessionIdAt(LIVE_START);

    @TempDir
    Path root;

    private Path project;
    private RemoteClients clients;

    @BeforeEach
    void setUp() throws IOException {
        Path workspace = Files.createDirectories(root.resolve("prod"));
        project = VolumeWriter.project(workspace, "orders");
        clients = clients(new FilesystemVolume(root));
    }

    private static RemoteClients clients(VolumeAccess volume) {
        return clients(volume, !(volume instanceof WritableVolume));
    }

    private static RemoteClients clients(VolumeAccess volume, boolean readOnly) {
        return VolumeRemoteClients.of(new VolumeSource(volume, DESCRIPTION, readOnly));
    }

    /** A session started at {@code start}, named as the provisioner names it. */
    private Path session(String instanceId, Instant start) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId), start);
    }

    private static String id(Instant start) {
        return VolumeWriter.sessionIdAt(start);
    }

    /** The instance's newest session, with an older chunk and its newest recording five minutes later. */
    private Path liveSession() {
        Path dir = session("pod-a", LIVE_START);
        VolumeWriter.file(dir, "rec-20260220-121000+0000.jfr", "older chunk");
        VolumeWriter.file(dir, "rec-20260220-121500+0000.jfr", "newest");
        return dir;
    }

    private static ErrorCode codeOf(Runnable call) {
        return assertThrows(JeffreyException.class, call::run).getCode();
    }

    @Nested
    class Discovery {

        /** The list is one listing of the root: each workspace by its id, no marker read. */
        @Test
        void everyDirectoryAtTheRootIsAWorkspaceListedByItsId() throws IOException {
            Files.createDirectories(root.resolve("staging"));

            List<WorkspaceInfo> workspaces = clients.discovery().allWorkspaces();

            assertEquals(List.of("prod", "staging"), workspaces.stream().map(WorkspaceInfo::id).toList());
            assertEquals(List.of("prod", "staging"), workspaces.stream().map(WorkspaceInfo::name).toList());
        }

        /** One workspace, opened, is its directory: named by its id, created when the directory was. */
        @Test
        void oneWorkspaceIsItsDirectory() {
            WorkspaceInfo prod = clients.discovery().workspaceOrThrow("prod").orElseThrow();

            assertEquals("prod", prod.name());
            assertNotNull(prod.createdAt());
            assertEquals(Optional.empty(), clients.discovery().workspaceOrThrow("missing"));
        }

        @Test
        void aProjectIsItsDirectoryAndCarriesNoMeasuredSize() {
            liveSession();
            VolumeWriter.project(root.resolve("prod"), "idle");

            List<RemoteProjectResponse> projects = clients.discovery().allProjects("prod");

            assertEquals(List.of("idle", "orders"), projects.stream().map(RemoteProjectResponse::id).toList());
            assertEquals(List.of("idle", "orders"), projects.stream().map(RemoteProjectResponse::name).toList());
            RemoteProjectResponse orders = clients.discovery().project("prod", "orders").orElseThrow();
            assertEquals(RepositoryStatistics.NOT_YET_MEASURED, orders.statistics());
            assertEquals(Optional.empty(), clients.discovery().project("prod", "gone"));
        }

        @Test
        void reportsItselfWhereAHubReportsItsVersion() {
            assertEquals(DESCRIPTION, clients.discovery().info().version());
        }

        /** An unreadable volume is reported like an unreachable hub, which every caller already handles. */
        @Test
        void aVolumeThatCannotBeReadIsUnavailable() {
            RemoteClients unreadable = clients(new UnreadableVolume());

            assertEquals(ErrorCode.REMOTE_UNAVAILABLE, codeOf(() -> unreadable.discovery().info()));
        }
    }

    @Nested
    class Repository {

        @Test
        void aListingCarriesEachSessionsSizeButNotItsFiles() {
            liveSession();

            RecordingSessionResponse listed = clients.repository().recordingSessions(ORDERS).getFirst();

            assertEquals("pod-a/" + LIVE, listed.name());
            assertEquals(2, listed.fileCount());
            assertEquals("older chunk".length() + "newest".length(), listed.sizeBytes());
            assertEquals(List.of(), listed.files());
        }

        /**
         * The window is judged by when each session's data ends, read off its chunk names: a session
         * whose one chunk started at 12:00 is out of the last 45 minutes, the live one (to 12:20) is in.
         */
        @Test
        void aFilterIsAppliedBeforeAnyFileIsRead() {
            liveSession();
            VolumeWriter.file(session("pod-b", T0), "rec-20260220-120000+0000.jfr", "chunk");

            List<RecordingSessionResponse> recent = clients.repository().recordingSessions(
                    ORDERS, RecordingSessionFilter.activeWithinLast(Duration.ofMinutes(45), NOW));

            assertEquals(List.of(LIVE), recent.stream().map(RecordingSessionResponse::id).toList());
        }

        /** Its data ends one measured chunk (five minutes) after its newest recording started. */
        @Test
        void oneSessionCarriesItsFilesWithItsNewestRecordingMarked() {
            liveSession();

            RecordingSessionResponse session = clients.repository().recordingSession(ORDERS, LIVE);

            assertTrue(session.newest());
            assertEquals(List.of(true, false),
                    session.files().stream().map(RepositoryFileResponse::newestRecording).toList());
            assertEquals("rec-20260220-121500+0000", session.files().getFirst().id());
            assertEquals(Instant.parse("2026-02-20T12:20:00Z").toEpochMilli(), session.lastDataAt());
        }

        @Test
        void anEmptySessionFailedOnceANewerOneStartedButTheNewestNever() {
            session("pod-a", T0);
            session("pod-a", T0.plusSeconds(60));
            session("pod-b", T0.plusSeconds(30));

            assertTrue(clients.repository().recordingSession(ORDERS, id(T0)).failed());
            RecordingSessionResponse next = clients.repository().recordingSession(ORDERS, id(T0.plusSeconds(60)));
            assertTrue(next.newest());
            assertFalse(next.failed());
            assertFalse(clients.repository().recordingSession(ORDERS, id(T0.plusSeconds(30))).failed());
            assertNull(next.lastDataAt());
        }

        /**
         * A session the project does not hold is not found. A project that is not there reads as
         * empty: the reads of a request take the project its lookup already resolved, without
         * looking at it again.
         */
        @Test
        void anUnknownSessionIsNotFoundAndAnUnknownProjectHoldsNone() {
            assertTrue(codeOf(() -> clients.repository().recordingSession(ORDERS, "missing")).isNotFound());
            assertTrue(codeOf(() -> clients.repository().recordingSession(ORDERS, id(T0))).isNotFound());
            assertEquals(List.of(), clients.repository().recordingSessions(new ProjectScope("prod", "gone")));
        }
    }

    /** The user's changes, made as the hub makes them on its own mount. */
    @Nested
    class Changes {

        @Test
        void createsAWorkspaceAsItsDirectoryAlone() throws IOException {
            WorkspaceInfo created = clients.discovery().createWorkspace("staging");

            assertEquals("staging", created.id());
            assertEquals("staging", created.name());
            try (var entries = Files.list(root.resolve("staging"))) {
                assertEquals(0, entries.count(), "nothing is written into it");
            }
        }

        /** The provisioner may write into a workspace's directory first: it is a workspace already. */
        @Test
        void refusesAnIdWhoseDirectoryIsThereAndAnInvalidId() {
            assertThrows(JeffreyClientException.class, () -> clients.discovery().createWorkspace("prod"));
            assertTrue(Files.isDirectory(project), "the project already written there stays");
            assertThrows(JeffreyClientException.class, () -> clients.discovery().createWorkspace("../up"));
        }

        @Test
        void deletesAWorkspaceWithEverythingInIt() {
            liveSession();

            clients.discovery().deleteWorkspace("prod");

            assertFalse(Files.exists(root.resolve("prod")));
            assertTrue(codeOf(() -> clients.discovery().deleteWorkspace("prod")).isNotFound());
        }

        @Test
        void deletesAProject() {
            liveSession();

            clients.projects().deleteProject(ORDERS);

            assertFalse(Files.exists(project));
            assertTrue(Files.isDirectory(root.resolve("prod")));
        }

        @Test
        void deletingASessionTakesItsInstanceOnlyWithItsLastSession() {
            Path first = session("pod-a", T0);
            Path second = session("pod-a", T0.plusSeconds(60));

            clients.repository().deleteSession(ORDERS, id(T0));
            assertFalse(Files.exists(first));
            assertTrue(Files.isDirectory(second));

            clients.repository().deleteSession(ORDERS, id(T0.plusSeconds(60)));
            assertFalse(Files.exists(second.getParent()));
            assertTrue(codeOf(() -> clients.repository().deleteSession(ORDERS, id(T0.plusSeconds(60)))).isNotFound());
        }
    }

    /** A source declared read-only refuses every change and leaves the volume as it was. */
    @Nested
    class ReadOnly {

        @Test
        void changesNothing() {
            RemoteClients readOnly = clients(new FilesystemVolume(root), true);
            Path dir = liveSession();

            assertThrows(JeffreyClientException.class, () -> readOnly.discovery().createWorkspace("new-ws"));
            assertThrows(JeffreyClientException.class, () -> readOnly.discovery().deleteWorkspace("prod"));
            assertThrows(JeffreyClientException.class, () -> readOnly.repository().deleteSession(ORDERS, LIVE));
            assertThrows(JeffreyClientException.class, () -> readOnly.projects().deleteProject(ORDERS));
            assertTrue(Files.isDirectory(dir));
            assertFalse(Files.exists(root.resolve("new-ws")));
        }

        @Test
        void aSourceThatMayBeChangedNeedsAWritableVolume() {
            assertThrows(IllegalArgumentException.class, () -> clients(new UnreadableVolume(), false));
        }
    }

    @Nested
    class Instances {

        /** Each instance is its directory: one listing of the project, nothing below it read. */
        @Test
        void eachIsItsDirectory() {
            liveSession();
            VolumeWriter.instance(project, "pod-new");

            List<InstanceResponse> instances = clients.instances().projectInstances(ORDERS);

            assertEquals(List.of("pod-a", "pod-new"), instances.stream().map(InstanceResponse::id).toList());
            assertTrue(instances.stream().allMatch(instance -> instance.createdAt() != null),
                    "the directory's own time, as the volume lists it");
        }

        /** One instance names a page of its sessions, newest first; no session on it is read. */
        @Test
        void oneInstanceNamesItsSessionsNewestFirst() {
            liveSession();
            session("pod-a", T0);

            InstanceWithSessionsResponse read = clients.instances().instance(ORDERS, "pod-a", InstancePage.whole()).orElseThrow();

            assertEquals(List.of(LIVE, id(T0)), read.sessions().stream().map(RecordingSessionHeaderResponse::id).toList());
            assertEquals(List.of(true, false),
                    read.sessions().stream().map(RecordingSessionHeaderResponse::newest).toList());
            assertEquals(LIVE_START.toEpochMilli(), read.sessions().getFirst().createdAt());
            assertEquals("pod-a", read.sessions().getFirst().instanceId());
            assertEquals(Optional.empty(), clients.instances().instance(ORDERS, "pod-z", InstancePage.whole()));
        }
    }

    @Nested
    class FileStreaming {

        private String stream(String sessionId, String fileId) {
            StringBuilder received = new StringBuilder();
            clients.files().streamFile(ORDERS, sessionId, fileId, (in, file) ->
                    received.append(file.name()).append('=').append(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            return received.toString();
        }

        @Test
        void aChunkStreamsUnderItsName() {
            liveSession();

            assertEquals("rec-20260220-121000+0000.jfr=older chunk", stream(LIVE, "rec-20260220-121000+0000"));
        }

        /**
         * The newest recording is served like any other: the profiler writes a file whole, and the
         * receiver checks its chunk headers before it keeps it.
         */
        @Test
        void theNewestRecordingIsServedToo() {
            liveSession();

            assertEquals("rec-20260220-121500+0000.jfr=newest", stream(LIVE, "rec-20260220-121500+0000"));
        }

        @Test
        void aFileTheSessionDoesNotHoldIsRefused() {
            liveSession();

            assertThrows(JeffreyClientException.class, () -> stream(LIVE, "profile-19700101-000000"));
        }

        @Test
        void anEmptyRecordingIsRefusedButAnEmptyLogIsServed() {
            Path dir = session("pod-a", T0);
            VolumeWriter.file(dir, "rec-20260220-120000+0000.jfr", "");
            VolumeWriter.file(dir, "app.log", "");

            assertThrows(JeffreyClientException.class, () -> stream(id(T0), "rec-20260220-120000+0000"));
            assertEquals("app.log=", stream(id(T0), "app.log"));
        }

        /** Mid-compression a recording and its archive share an id; the archive is what stays. */
        @Test
        void theArchiveIsServedWhenARecordingAndItsArchiveShareAnId() {
            Path dir = session("pod-a", T0);
            VolumeWriter.file(dir, "rec-20260220-120000+0000.jfr", "raw");
            VolumeWriter.file(dir, "rec-20260220-120000+0000.jfr.lz4", "archived");

            assertEquals("rec-20260220-120000+0000.jfr.lz4=archived", stream(id(T0), "rec-20260220-120000+0000"));
        }
    }

    /** A volume whose storage refuses every look, as a share with a rejected credential does. */
    private static final class UnreadableVolume implements VolumeAccess {

        @Override
        public List<VolumeEntry> list(VolumePath directory) {
            throw refused();
        }

        @Override
        public Optional<VolumeEntry> stat(VolumePath path) {
            throw refused();
        }

        @Override
        public Optional<byte[]> read(VolumePath file) {
            throw refused();
        }

        @Override
        public Optional<OpenedFile> open(VolumePath file) {
            throw refused();
        }

        private static UncheckedIOException refused() {
            return new UncheckedIOException(new IOException("AuthenticationFailed"));
        }
    }
}
