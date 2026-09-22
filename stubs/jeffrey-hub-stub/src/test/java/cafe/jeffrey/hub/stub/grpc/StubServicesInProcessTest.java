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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.DataChunk;
import cafe.jeffrey.hub.api.v1.DownloadFileRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoResponse;
import cafe.jeffrey.hub.api.v1.GetInstanceSessionDetailRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceSessionDetailResponse;
import cafe.jeffrey.hub.api.v1.InstanceSessionInfo;
import cafe.jeffrey.hub.api.v1.GetRepositoryStatisticsRequest;
import cafe.jeffrey.hub.api.v1.GetRepositoryStatisticsResponse;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.api.v1.ListProjectsRequest;
import cafe.jeffrey.hub.api.v1.ListProjectsResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.FileDownloadServiceGrpc;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataFactory;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubServicesInProcessTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);

    private static Server server;
    private static ManagedChannel channel;
    private static StubDataset dataset;

    @BeforeAll
    static void startServer() throws IOException {
        dataset = new StubDataFactory(FIXED_CLOCK).create();
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new StubWorkspaceService(dataset))
                .addService(new StubProjectService(dataset))
                .addService(new StubInstanceService(dataset))
                .addService(new StubRepositoryService(dataset))
                .addService(new StubFileDownloadService(dataset))
                .addService(new StubScopedConfigService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void getApiInfoReportsApiVersionOne() {
        GetApiInfoResponse response = WorkspaceServiceGrpc.newBlockingStub(channel)
                .getApiInfo(GetApiInfoRequest.getDefaultInstance());

        assertEquals(1, response.getApiVersion());
        assertFalse(response.getVersion().isEmpty());
    }

    @Test
    void listWorkspacesReturnsTheSeededWorkspaces() {
        ListWorkspacesResponse response = WorkspaceServiceGrpc.newBlockingStub(channel)
                .listWorkspaces(ListWorkspacesRequest.getDefaultInstance());

        assertEquals(dataset.workspaces().size(), response.getWorkspacesCount());
        assertTrue(response.getWorkspacesList().stream().anyMatch(workspace -> workspace.getName().equals("Production")));
    }

    @Test
    void listProjectsExcludesDeletedByDefault() {
        WorkspaceInfo development = WorkspaceServiceGrpc.newBlockingStub(channel)
                .listWorkspaces(ListWorkspacesRequest.getDefaultInstance())
                .getWorkspacesList().stream()
                .filter(workspace -> workspace.getName().equals("Development"))
                .findFirst()
                .orElseThrow();

        ProjectServiceGrpc.ProjectServiceBlockingStub projects = ProjectServiceGrpc.newBlockingStub(channel);

        ListProjectsResponse active = projects.listProjects(ListProjectsRequest.newBuilder()
                .setWorkspaceId(development.getId())
                .build());
        ListProjectsResponse all = projects.listProjects(ListProjectsRequest.newBuilder()
                .setWorkspaceId(development.getId())
                .setIncludeDeleted(true)
                .build());

        assertTrue(all.getProjectsCount() > active.getProjectsCount());
    }

    @Test
    void listInstancesAndStatisticsArePopulated() {
        String projectId = dataset.workspaces().getFirst().projects().getFirst().id();

        ListInstancesResponse instances = InstanceServiceGrpc.newBlockingStub(channel)
                .listInstances(ListInstancesRequest.newBuilder()
                        .setProjectId(projectId)
                        .setIncludeSessions(true)
                        .build());
        assertFalse(instances.getInstancesList().isEmpty());
        assertTrue(instances.getInstancesList().stream().anyMatch(instance -> instance.getSessionsCount() > 0));

        GetRepositoryStatisticsResponse stats = RepositoryServiceGrpc.newBlockingStub(channel)
                .getRepositoryStatistics(GetRepositoryStatisticsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());
        assertTrue(stats.getTotalSize() > 0);
    }

    @Test
    void firstWorkspaceHasProjects() {
        ProjectInfo first = ProjectServiceGrpc.newBlockingStub(channel)
                .listProjects(ListProjectsRequest.newBuilder()
                        .setWorkspaceId(dataset.workspaces().getFirst().id())
                        .build())
                .getProjects(0);
        assertFalse(first.getId().isEmpty());
    }

    @Test
    void downloadFileStreamsTheBundledJfrForARecordingOfAKnownSession() {
        String sessionId = firstSessionId();

        Iterator<DataChunk> chunks = FileDownloadServiceGrpc.newBlockingStub(channel)
                .downloadFile(DownloadFileRequest.newBuilder()
                        .setSessionId(sessionId)
                        .setFileId(fileId(sessionId, true))
                        .build());

        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        long totalSizeFromFirstChunk = -1;
        String nameFromFirstChunk = null;
        boolean firstChunk = true;
        while (chunks.hasNext()) {
            DataChunk chunk = chunks.next();
            if (firstChunk) {
                totalSizeFromFirstChunk = chunk.getTotalSize();
                nameFromFirstChunk = chunk.getFilename();
                firstChunk = false;
            }
            collected.writeBytes(chunk.getData().toByteArray());
        }

        byte[] bytes = collected.toByteArray();
        assertTrue(bytes.length > 0, "expected a non-empty recording file");
        assertEquals(bytes.length, totalSizeFromFirstChunk, "total_size must match the streamed byte count");
        assertEquals(fileName(sessionId, true), nameFromFirstChunk,
                "the name travels with the bytes, on the first chunk");
        // LZ4 frame magic (0x04 0x22 0x4D 0x18) — confirms the bundled .jfr.lz4 streamed intact.
        assertEquals(0x04, bytes[0] & 0xFF);
        assertEquals(0x22, bytes[1] & 0xFF);
        assertEquals(0x4D, bytes[2] & 0xFF);
        assertEquals(0x18, bytes[3] & 0xFF);
    }

    @Test
    void downloadFileAlwaysReturnsTheSameRecordingBytesAcrossSessions() {
        FileDownloadServiceGrpc.FileDownloadServiceBlockingStub stub =
                FileDownloadServiceGrpc.newBlockingStub(channel);

        long first = countBytes(stub, "sess-inst-checkout-blue-1");
        long second = countBytes(stub, "sess-inst-inventory-1-1");

        assertTrue(first > 0);
        assertEquals(first, second, "the stub serves the same fixed file for every session");
    }

    /**
     * The same RPC, and the file's own kind is the only thing that differs: an artifact has no
     * bytes here, so it arrives as the one header chunk that names it.
     */
    @Test
    void downloadFileReturnsAnEmptyArtifactThatStillCarriesItsName() {
        String sessionId = firstSessionId();

        Iterator<DataChunk> chunks = FileDownloadServiceGrpc.newBlockingStub(channel)
                .downloadFile(DownloadFileRequest.newBuilder()
                        .setSessionId(sessionId)
                        .setFileId(fileId(sessionId, false))
                        .build());

        long total = 0;
        String nameFromFirstChunk = null;
        while (chunks.hasNext()) {
            DataChunk chunk = chunks.next();
            if (nameFromFirstChunk == null) {
                nameFromFirstChunk = chunk.getFilename();
            }
            total += chunk.getData().size();
        }
        assertEquals(0, total, "artifacts are served as empty files");
        assertEquals(fileName(sessionId, false), nameFromFirstChunk,
                "an empty file still sends the header chunk that names it");
    }

    @Test
    void downloadFileForUnknownSessionReturnsNotFound() {
        assertDownloadFails("does-not-exist", "any-chunk");
    }

    @Test
    void downloadFileForUnknownFileReturnsNotFound() {
        assertDownloadFails(firstSessionId(), "does-not-exist");
    }

    private void assertDownloadFails(String sessionId, String fileId) {
        Iterator<DataChunk> chunks = FileDownloadServiceGrpc.newBlockingStub(channel)
                .downloadFile(DownloadFileRequest.newBuilder()
                        .setSessionId(sessionId)
                        .setFileId(fileId)
                        .build());

        StatusRuntimeException error = assertThrows(StatusRuntimeException.class, () -> {
            while (chunks.hasNext()) {
                chunks.next();
            }
        });
        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
    }

    private long countBytes(
            FileDownloadServiceGrpc.FileDownloadServiceBlockingStub stub, String sessionId) {
        Iterator<DataChunk> chunks = stub.downloadFile(DownloadFileRequest.newBuilder()
                .setSessionId(sessionId)
                .setFileId(fileId(sessionId, true))
                .build());
        long total = 0;
        while (chunks.hasNext()) {
            total += chunks.next().getData().size();
        }
        return total;
    }

    private String firstSessionId() {
        String projectId = dataset.workspaces().getFirst().projects().getFirst().id();
        return dataset.sessionsForProject(projectId).getFirst().id();
    }

    private StubDataset.File file(String sessionId, boolean recording) {
        return dataset.session(sessionId)
                .orElseThrow(() -> new IllegalStateException("no fixture session " + sessionId))
                .files().stream()
                .filter(file -> file.kind().recording() == recording)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "session " + sessionId + " holds no " + (recording ? "recording" : "artifact")));
    }

    private String fileId(String sessionId, boolean recording) {
        return file(sessionId, recording).id();
    }

    private String fileName(String sessionId, boolean recording) {
        return file(sessionId, recording).name();
    }

    /**
     * The session detail carries the session and nothing read out of its recording. The hub
     * serves files; Microscope pulls the session's newest closed chunk and parses it there.
     */
    @Test
    void sessionDetailCarriesSessionMetadataOnly() {
        InstanceServiceGrpc.InstanceServiceBlockingStub instances = InstanceServiceGrpc.newBlockingStub(channel);
        String projectId = dataset.workspaces().getFirst().projects().getFirst().id();

        ListInstancesResponse listed = instances.listInstances(ListInstancesRequest.newBuilder()
                .setProjectId(projectId)
                .setIncludeSessions(true)
                .build());

        String instanceId = null;
        InstanceSessionInfo finishedSession = null;
        outer:
        for (var instance : listed.getInstancesList()) {
            for (InstanceSessionInfo session : instance.getSessionsList()) {
                if (!session.getIsActive()) {
                    instanceId = instance.getId();
                    finishedSession = session;
                    break outer;
                }
            }
        }
        assertFalse(instanceId == null, "expected at least one finished session");

        GetInstanceSessionDetailResponse detail = instances.getInstanceSessionDetail(
                GetInstanceSessionDetailRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .setSessionId(finishedSession.getId())
                        .build());

        assertEquals(finishedSession.getId(), detail.getSession().getId());
        assertFalse(detail.getSession().getIsActive());
    }
}
