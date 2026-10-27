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
import cafe.jeffrey.microscope.remote.client.InstanceRead;
import cafe.jeffrey.microscope.remote.client.InstancePage;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.ProjectActivity;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.shared.volume.layout.WorkspaceMarker;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");

    @TempDir
    Path root;

    private Path project;
    private RemoteClients clients;

    @BeforeEach
    void setUp() throws IOException {
        Path workspace = Files.createDirectories(root.resolve("prod"));
        Files.writeString(workspace.resolve(WorkspaceMarker.FILE_NAME),
                Json.toString(new WorkspaceMarker("Production", T0.toEpochMilli())));
        project = VolumeWriter.project(workspace, "p-orders", "orders", T0);
        clients = clients(new FilesystemVolume(root));
    }

    private static RemoteClients clients(VolumeAccess volume) {
        return clients(volume, !(volume instanceof WritableVolume));
    }

    private static RemoteClients clients(VolumeAccess volume, boolean readOnly) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return VolumeRemoteClients.of(new VolumeSource(volume, clock, DESCRIPTION, readOnly));
    }

    private Path session(String instanceId, String sessionId, Instant createdAt) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId, T0), sessionId, createdAt, null);
    }

    /** The instance's newest session, with an older chunk and its newest recording five minutes later. */
    private Path liveSession() {
        Path dir = session("pod-a", "s-live", T0.plusSeconds(600));
        VolumeWriter.file(dir, "profile-20260220-121000.jfr", "older chunk");
        VolumeWriter.file(dir, "profile-20260220-121500.jfr", "newest");
        return dir;
    }

    private static ErrorCode codeOf(Runnable call) {
        return assertThrows(JeffreyException.class, call::run).getCode();
    }

    @Nested
    class Discovery {

        @Test
        void everyDirectoryAtTheRootIsAWorkspaceNamedByItsMarkerOrItsId() throws IOException {
            Files.createDirectories(root.resolve("staging"));

            List<WorkspaceInfo> workspaces = clients.discovery().allWorkspaces();

            assertEquals(List.of("Production", "staging"), workspaces.stream().map(WorkspaceInfo::name).toList());
            assertEquals(List.of(1, 0), workspaces.stream().map(WorkspaceInfo::projectCount).toList());
            assertEquals(T0, workspaces.getFirst().createdAt());
        }

        @Test
        void aProjectCarriesNoMeasuredSize() {
            liveSession();
            VolumeWriter.project(root.resolve("prod"), "p-idle", "idle", T0);

            List<RemoteProjectResponse> projects = clients.discovery().allProjects("prod");

            assertEquals(List.of("idle", "orders"), projects.stream().map(RemoteProjectResponse::name).sorted().toList());
            RemoteProjectResponse orders = clients.discovery().project("prod", "p-orders").orElseThrow();
            assertEquals(RepositoryStatistics.NOT_YET_MEASURED, orders.statistics());
        }

        @Test
        void aProjectsActivityIsReadOffItsInstances() {
            liveSession();
            VolumeWriter.project(root.resolve("prod"), "p-idle", "idle", T0);

            assertEquals(new ProjectActivity(Instant.parse("2026-02-20T12:20:00Z"), 1),
                    clients.instances().activity(ORDERS));
            assertEquals(new ProjectActivity(null, 0),
                    clients.instances().activity(new ProjectScope("prod", "p-idle")));
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

            assertEquals("pod-a/s-live", listed.name());
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
            VolumeWriter.file(session("pod-b", "s-old", T0), "profile-20260220-120000.jfr", "chunk");

            List<RecordingSessionResponse> recent = clients.repository().recordingSessions(
                    ORDERS, RecordingSessionFilter.activeWithinLast(Duration.ofMinutes(45), NOW));

            assertEquals(List.of("s-live"), recent.stream().map(RecordingSessionResponse::id).toList());
        }

        /** Its data ends one measured chunk (five minutes) after its newest recording started. */
        @Test
        void oneSessionCarriesItsFilesWithItsNewestRecordingMarked() {
            liveSession();

            RecordingSessionResponse session = clients.repository().recordingSession(ORDERS, "s-live");

            assertTrue(session.newest());
            assertEquals(List.of(true, false),
                    session.files().stream().map(RepositoryFileResponse::newestRecording).toList());
            assertEquals("profile-20260220-121500", session.files().getFirst().id());
            assertEquals(Instant.parse("2026-02-20T12:20:00Z").toEpochMilli(), session.lastDataAt());
        }

        @Test
        void anEmptySessionFailedOnceANewerOneStartedButTheNewestNever() {
            session("pod-a", "s-crashed", T0);
            session("pod-a", "s-next", T0.plusSeconds(60));
            session("pod-b", "s-quiet", T0);

            assertTrue(clients.repository().recordingSession(ORDERS, "s-crashed").failed());
            RecordingSessionResponse next = clients.repository().recordingSession(ORDERS, "s-next");
            assertTrue(next.newest());
            assertFalse(next.failed());
            assertFalse(clients.repository().recordingSession(ORDERS, "s-quiet").failed());
            assertNull(next.lastDataAt());
        }

        @Test
        void anUnknownSessionOrProjectIsNotFound() {
            assertTrue(codeOf(() -> clients.repository().recordingSession(ORDERS, "missing")).isNotFound());
            assertTrue(codeOf(() -> clients.repository().recordingSessions(new ProjectScope("prod", "p-gone")))
                    .isNotFound());
        }
    }

    /** The user's changes, made as the hub makes them on its own mount. */
    @Nested
    class Changes {

        @Test
        void createsAWorkspaceDescribedByItsMarker() throws IOException {
            WorkspaceInfo created = clients.discovery().createWorkspace("staging", " Staging ");

            assertEquals("Staging", created.name());
            assertEquals(new WorkspaceMarker("Staging", NOW.toEpochMilli()),
                    Json.read(Files.readString(root.resolve("staging").resolve(WorkspaceMarker.FILE_NAME)),
                            WorkspaceMarker.class));
        }

        /** The provisioner may write into a workspace's directory before anyone describes it. */
        @Test
        void adoptsADirectoryTheProvisionerAlreadyWroteInto() throws IOException {
            VolumeWriter.project(Files.createDirectories(root.resolve("auto")), "p-auto", "auto", T0);

            assertEquals(1, clients.discovery().createWorkspace("auto", "Auto").projectCount());
        }

        @Test
        void refusesAnIdAlreadyDescribedANameAlreadyTakenAndAnInvalidId() {
            assertThrows(JeffreyClientException.class, () -> clients.discovery().createWorkspace("prod", "Other"));
            assertThrows(JeffreyClientException.class,
                    () -> clients.discovery().createWorkspace("other", "Production"));
            assertThrows(JeffreyClientException.class, () -> clients.discovery().createWorkspace("../up", "Up"));
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
            Path first = session("pod-a", "s-1", T0);
            Path second = session("pod-a", "s-2", T0.plusSeconds(60));

            clients.repository().deleteSession(ORDERS, "s-1");
            assertFalse(Files.exists(first));
            assertTrue(Files.isDirectory(second));

            clients.repository().deleteSession(ORDERS, "s-2");
            assertFalse(Files.exists(second.getParent()));
            assertTrue(codeOf(() -> clients.repository().deleteSession(ORDERS, "s-2")).isNotFound());
        }
    }

    /** A source declared read-only refuses every change and leaves the volume as it was. */
    @Nested
    class ReadOnly {

        @Test
        void changesNothing() {
            RemoteClients readOnly = clients(new FilesystemVolume(root), true);
            Path dir = liveSession();

            assertThrows(JeffreyClientException.class, () -> readOnly.discovery().createWorkspace("new-ws", "New"));
            assertThrows(JeffreyClientException.class, () -> readOnly.discovery().deleteWorkspace("prod"));
            assertThrows(JeffreyClientException.class, () -> readOnly.repository().deleteSession(ORDERS, "s-live"));
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

        @Test
        void eachIsReadOffItsNewestSession() {
            liveSession();
            VolumeWriter.instance(project, "pod-new", T0);

            List<InstanceResponse> instances = clients.instances().projectInstances(ORDERS);

            assertEquals(List.of(1, 0), instances.stream().map(InstanceResponse::sessionCount).toList());
            assertEquals(Instant.parse("2026-02-20T12:20:00Z").toEpochMilli(), instances.getFirst().lastDataAt());
            assertNull(instances.getLast().lastDataAt());
        }

        @Test
        void oneInstanceComesWithItsSessionsSizedButWithoutFiles() {
            liveSession();

            InstanceRead read = clients.instances().instance(ORDERS, "pod-a", InstancePage.whole()).orElseThrow();

            assertEquals(1, read.instance().sessionCount());
            assertEquals(2, read.sessions().getFirst().fileCount());
            assertEquals(List.of(), read.sessions().getFirst().files());
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

            assertEquals("profile-20260220-121000.jfr=older chunk", stream("s-live", "profile-20260220-121000"));
        }

        /**
         * The newest recording is served like any other: the profiler writes a file whole, and the
         * receiver checks its chunk headers before it keeps it.
         */
        @Test
        void theNewestRecordingIsServedToo() {
            liveSession();

            assertEquals("profile-20260220-121500.jfr=newest", stream("s-live", "profile-20260220-121500"));
        }

        @Test
        void aFileTheSessionDoesNotHoldIsRefused() {
            liveSession();

            assertThrows(JeffreyClientException.class, () -> stream("s-live", "profile-19700101-000000"));
        }

        @Test
        void anEmptyRecordingIsRefusedButAnEmptyLogIsServed() {
            Path dir = session("pod-a", "s-done", T0);
            VolumeWriter.file(dir, "profile-20260220-120000.jfr", "");
            VolumeWriter.file(dir, "app.log", "");

            assertThrows(JeffreyClientException.class, () -> stream("s-done", "profile-20260220-120000"));
            assertEquals("app.log=", stream("s-done", "app.log"));
        }

        /** Mid-compression a recording and its archive share an id; the archive is what stays. */
        @Test
        void theArchiveIsServedWhenARecordingAndItsArchiveShareAnId() {
            Path dir = session("pod-a", "s-done", T0);
            VolumeWriter.file(dir, "profile-20260220-120000.jfr", "raw");
            VolumeWriter.file(dir, "profile-20260220-120000.jfr.lz4", "archived");

            assertEquals("profile-20260220-120000.jfr.lz4=archived", stream("s-done", "profile-20260220-120000"));
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
