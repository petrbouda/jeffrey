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
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
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
import cafe.jeffrey.shared.volume.liveness.HeartbeatReader;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
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
 * A recording volume reached straight on its storage answers the hub clients the way a hub would:
 * the same tree, the same session statuses, the same refusals, the same changes — or none at all
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
        SessionLiveness liveness = new SessionLiveness(
                clock, new HeartbeatReader(volume), new SessionDeadlines(Duration.ofSeconds(10), Duration.ofMinutes(1)));
        return VolumeRemoteClients.of(new VolumeSource(volume, liveness, clock, DESCRIPTION, readOnly));
    }

    private Path session(String instanceId, String sessionId, Instant createdAt) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId, T0), sessionId, createdAt, null);
    }

    /** A live session with a closed chunk and the one its profiler is still writing. */
    private Path liveSession() {
        Path dir = session("pod-a", "s-live", T0.plusSeconds(600));
        VolumeWriter.heartbeat(dir, NOW);
        VolumeWriter.file(dir, "profile-20260220-121000.jfr", "closed chunk");
        VolumeWriter.file(dir, "profile-20260220-121500.jfr", "open");
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
        void aProjectCarriesItsStatusAndSessionCountButNoMeasuredSize() {
            liveSession();
            VolumeWriter.project(root.resolve("prod"), "p-idle", "idle", T0);

            List<RemoteProjectResponse> projects = clients.discovery().allProjects("prod");

            assertEquals(List.of("idle", "orders"), projects.stream().map(RemoteProjectResponse::name).sorted().toList());
            RemoteProjectResponse orders = clients.discovery().project("prod", "p-orders").orElseThrow();
            assertEquals(RecordingStatus.ACTIVE, orders.status());
            assertEquals(1, orders.sessionCount());
            assertEquals(RepositoryStatistics.NOT_YET_MEASURED, orders.statistics());
            assertEquals(RecordingStatus.UNKNOWN, clients.discovery().project("prod", "p-idle").orElseThrow().status());
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
            assertTrue(unreadable.discovery().tryInfo().isEmpty());
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
            assertEquals("closed chunk".length() + "open".length(), listed.sizeBytes());
            assertEquals(List.of(), listed.files());
        }

        @Test
        void aFilterIsAppliedBeforeAnyFileIsRead() {
            liveSession();
            VolumeWriter.finished(session("pod-b", "s-done", T0), T0.plusSeconds(60));

            List<RecordingSessionResponse> live = clients.repository().recordingSessions(
                    ORDERS, RecordingSessionFilter.ALL.withStatus(RecordingStatus.ACTIVE));

            assertEquals(List.of("s-live"), live.stream().map(RecordingSessionResponse::id).toList());
        }

        /** The open chunk is the live session's newest recording; every other file is finished. */
        @Test
        void oneSessionCarriesItsFilesWithTheOpenChunkMarked() {
            liveSession();

            RecordingSessionResponse session = clients.repository().recordingSession(ORDERS, "s-live");

            assertEquals(RecordingStatus.ACTIVE, session.status());
            assertEquals(List.of(RecordingStatus.ACTIVE, RecordingStatus.FINISHED),
                    session.files().stream().map(RepositoryFileResponse::status).toList());
            assertEquals("profile-20260220-121500", session.files().getFirst().id());
            assertNull(session.finishedAt());
        }

        @Test
        void aSessionThatFinishedWithNoDataFailedButOneMissingItsHeartbeatDidNot() {
            VolumeWriter.finished(session("pod-a", "s-crashed", T0), T0.plusSeconds(5));
            session("pod-b", "s-silent", T0);

            assertTrue(clients.repository().recordingSession(ORDERS, "s-crashed").failed());
            RecordingSessionResponse silent = clients.repository().recordingSession(ORDERS, "s-silent");
            assertTrue(silent.heartbeatMissing());
            assertFalse(silent.failed());
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

        @Test
        void pinsASessionAndReleasesIt() {
            Path dir = liveSession();

            clients.repository().setSessionRetained(ORDERS, "s-live", true);
            assertTrue(Files.exists(dir.resolve(".retained")));
            assertTrue(clients.repository().recordingSession(ORDERS, "s-live").retained());

            clients.repository().setSessionRetained(ORDERS, "s-live", false);
            assertFalse(Files.exists(dir.resolve(".retained")));
            assertTrue(codeOf(() -> clients.repository().setSessionRetained(ORDERS, "missing", true)).isNotFound());
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
            assertThrows(JeffreyClientException.class,
                    () -> readOnly.repository().setSessionRetained(ORDERS, "s-live", true));
            assertThrows(JeffreyClientException.class, () -> readOnly.projects().deleteProject(ORDERS));
            assertTrue(Files.isDirectory(dir));
            assertFalse(Files.exists(root.resolve("new-ws")));
            assertFalse(Files.exists(dir.resolve(".retained")));
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

            assertEquals(List.of("ACTIVE", "PENDING"), instances.stream().map(InstanceResponse::status).toList());
        }

        @Test
        void oneInstanceComesWithItsSessionsSizedButWithoutFiles() {
            liveSession();

            InstanceRead read = clients.instances().instance(ORDERS, "pod-a").orElseThrow();

            assertEquals(1, read.instance().sessionCount());
            assertEquals(2, read.sessions().getFirst().fileCount());
            assertEquals(List.of(), read.sessions().getFirst().files());
            assertEquals(Optional.empty(), clients.instances().instance(ORDERS, "pod-z"));
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
        void aClosedChunkStreamsUnderItsName() {
            liveSession();

            assertEquals("profile-20260220-121000.jfr=closed chunk", stream("s-live", "profile-20260220-121000"));
        }

        @Test
        void theChunkStillBeingWrittenIsRefused() {
            liveSession();

            JeffreyClientException refused = assertThrows(JeffreyClientException.class,
                    () -> stream("s-live", "profile-20260220-121500"));
            assertTrue(refused.getMessage().contains("still writing"));
        }

        @Test
        void aFileTheSessionDoesNotHoldIsRefused() {
            liveSession();

            assertThrows(JeffreyClientException.class, () -> stream("s-live", "profile-19700101-000000"));
        }

        @Test
        void anEmptyRecordingIsRefusedButAnEmptyLogIsServed() {
            Path dir = session("pod-a", "s-done", T0);
            VolumeWriter.finished(dir, T0.plusSeconds(60));
            VolumeWriter.file(dir, "profile-20260220-120000.jfr", "");
            VolumeWriter.file(dir, "app.log", "");

            assertThrows(JeffreyClientException.class, () -> stream("s-done", "profile-20260220-120000"));
            assertEquals("app.log=", stream("s-done", "app.log"));
        }

        /** Mid-compression a recording and its archive share an id; the archive is what stays. */
        @Test
        void theArchiveIsServedWhenARecordingAndItsArchiveShareAnId() {
            Path dir = session("pod-a", "s-done", T0);
            VolumeWriter.finished(dir, T0.plusSeconds(60));
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
