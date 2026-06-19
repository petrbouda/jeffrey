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
import cafe.jeffrey.hub.api.v1.ListSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListSessionsResponse;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataFactory;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                .addService(new StubWorkspaceEventsService(dataset))
                .addService(new StubProfilerSettingsService())
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
        assertTrue(stats.getTotalFiles() > 0);
        assertTrue(stats.getTotalSize() > 0);
        assertTrue(stats.getJfrFiles() > 0);
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

    // Mirrors cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile names. The client
    // resolves file_type via SupportedRecordingFile.valueOf(), so an unknown name = null fileType = NPE.
    private static final Set<String> VALID_FILE_TYPES = Set.of(
            "JFR", "ASPROF_TEMP", "HEAP_DUMP_GZ", "HEAP_DUMP", "PERF_COUNTERS",
            "JVM_LOG", "HS_JVM_ERROR_LOG", "APP_LOG", "UNKNOWN");

    @Test
    void repositoryFileTypesAreValidSupportedRecordingFileNames() {
        String projectId = dataset.workspaces().getFirst().projects().getFirst().id();
        ListSessionsResponse sessions = RepositoryServiceGrpc.newBlockingStub(channel)
                .listSessions(ListSessionsRequest.newBuilder().setProjectId(projectId).build());

        long files = 0;
        for (var session : sessions.getSessionsList()) {
            for (RepositoryFile file : session.getFilesList()) {
                assertTrue(VALID_FILE_TYPES.contains(file.getFileType()),
                        "invalid file_type: " + file.getFileType());
                files++;
            }
        }
        assertTrue(files > 0, "expected at least one repository file");
    }

    @Test
    void finishedSessionDetailCarriesEnvironmentJson() {
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

        String env = detail.getEnvironmentJsonFields();
        assertFalse(env.isEmpty());
        assertTrue(env.contains("jdk.JVMInformation"));
        assertTrue(env.contains("jdk.GCHeapConfiguration"));
        assertTrue(env.contains("jdk.Shutdown"));
        assertTrue(env.contains("Shutdown requested from Java"));
    }
}
