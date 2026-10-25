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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.api.v1.DeleteProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectResponse;
import cafe.jeffrey.hub.api.v1.ListProjectsRequest;
import cafe.jeffrey.hub.api.v1.ListProjectsResponse;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.core.manager.project.HubProjectManager;
import cafe.jeffrey.hub.core.manager.project.HubProjectsManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspacesManager;
import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Projects over a real volume: a workspace directory holding project directories the provisioner
 * declared, each known by the project id in its marker.
 */
class ProjectGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @TempDir
    Path root;

    private Path projectDir;
    private StorageOverviewCache storageOverviewCache;
    private InProcessGrpcServer grpc;
    private ProjectServiceGrpc.ProjectServiceBlockingStub stub;

    @BeforeEach
    void start() {
        Path workspace = TestVolume.workspace(root, WORKSPACE_ID, "Workspace", FIXED_TIME);
        projectDir = TestVolume.project(workspace, PROJECT_ID, "Test Project", FIXED_TIME);
        TestVolume.workspace(root, "other-ws", "Other", FIXED_TIME);

        Clock clock = Clock.fixed(FIXED_TIME.plusSeconds(60), ZoneOffset.UTC);
        FilesystemVolume volume = new FilesystemVolume(root);
        ProjectManager.Factory projectFactory =
                project -> new HubProjectManager(project, volume, TestVolume.liveness(clock, volume));
        HubWorkspacesManager workspaces = new HubWorkspacesManager(volume, clock, false,
                ws -> new HubWorkspaceManager(ws, volume, dir -> new HubProjectsManager(dir, projectFactory)));

        storageOverviewCache = new StorageOverviewCache(clock);
        grpc = InProcessGrpcServer.start(new ProjectGrpcService(new GrpcLookups(workspaces), storageOverviewCache));
        stub = ProjectServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        grpc.close();
    }

    private static GetProjectRequest get(String workspaceId, String projectId) {
        return GetProjectRequest.newBuilder().setWorkspaceId(workspaceId).setProjectId(projectId).build();
    }

    private static void assertNotFound(Runnable call) {
        var ex = assertThrows(StatusRuntimeException.class, call::run);
        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }

    @Nested
    class ListProjects {

        @Test
        void returnsProjectList() {
            ListProjectsResponse response = stub.listProjects(
                    ListProjectsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(1, response.getProjectsCount());
            assertEquals(PROJECT_ID, response.getProjects(0).getId());
            assertEquals("Test Project", response.getProjects(0).getName());
            assertEquals(WORKSPACE_ID, response.getProjects(0).getWorkspaceId());
        }

        @Test
        void workspaceNotFound_returnsNotFound() {
            assertNotFound(() -> stub.listProjects(ListProjectsRequest.newBuilder().setWorkspaceId("missing").build()));
        }
    }

    @Nested
    class GetProject {

        @Test
        void returnsTheProjectWithItsSessions() {
            Path instance = TestVolume.instance(projectDir, "pod-a", FIXED_TIME);
            TestVolume.heartbeat(TestVolume.session(instance, "s-1", FIXED_TIME), FIXED_TIME.plusSeconds(60));

            GetProjectResponse response = stub.getProject(get(WORKSPACE_ID, PROJECT_ID));

            assertEquals(PROJECT_ID, response.getProject().getId());
            assertEquals(WORKSPACE_ID, response.getProject().getWorkspaceId());
            assertEquals(FIXED_TIME.toEpochMilli(), response.getProject().getCreatedAt());
            assertEquals(RecordingStatus.RECORDING_STATUS_ACTIVE, response.getProject().getStatus());
            assertEquals(1, response.getProject().getSessionCount());
        }

        /**
         * Only an instance's newest session can still be recording, so the older one — which has
         * no liveness files at all — is counted but never assessed.
         */
        @Test
        void statusComesFromEachInstancesNewestSession() {
            Path instance = TestVolume.instance(projectDir, "pod-a", FIXED_TIME);
            TestVolume.session(instance, "s-1", FIXED_TIME);
            TestVolume.heartbeat(TestVolume.session(instance, "s-2", FIXED_TIME.plusSeconds(30)), FIXED_TIME.plusSeconds(60));

            GetProjectResponse response = stub.getProject(get(WORKSPACE_ID, PROJECT_ID));

            assertEquals(RecordingStatus.RECORDING_STATUS_ACTIVE, response.getProject().getStatus());
            assertEquals(2, response.getProject().getSessionCount());
        }

        @Test
        void isFinishedOnceEveryNewestSessionFinished() {
            Path instance = TestVolume.instance(projectDir, "pod-a", FIXED_TIME);
            TestVolume.finished(TestVolume.session(instance, "s-1", FIXED_TIME), FIXED_TIME.plusSeconds(20));

            GetProjectResponse response = stub.getProject(get(WORKSPACE_ID, PROJECT_ID));

            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, response.getProject().getStatus());
        }

        @Test
        void carriesNoSizeBeforeTheFirstRetentionPass() {
            assertFalse(stub.getProject(get(WORKSPACE_ID, PROJECT_ID)).getProject().hasSizeBytes());
        }

        @Test
        void carriesTheSizeTheLastRetentionPassMeasured() {
            storageOverviewCache.publish(List.of(
                    new ProjectStorage(WORKSPACE_ID, "Workspace", PROJECT_ID, "Test Project", 4096L, 2, 0L)));

            assertEquals(4096L, stub.getProject(get(WORKSPACE_ID, PROJECT_ID)).getProject().getSizeBytes());
        }

        @Test
        void projectFromAnotherWorkspace_returnsNotFound() {
            assertNotFound(() -> stub.getProject(get("other-ws", PROJECT_ID)));
        }

        @Test
        void projectNotFound_returnsNotFound() {
            assertNotFound(() -> stub.getProject(get(WORKSPACE_ID, "missing")));
        }

        @Test
        void workspaceNotFound_returnsNotFound() {
            assertNotFound(() -> stub.getProject(get("missing", PROJECT_ID)));
        }
    }

    @Nested
    class DeleteProject {

        @Test
        void deletesTheProjectDirectory() {
            stub.deleteProject(DeleteProjectRequest.newBuilder()
                    .setWorkspaceId(WORKSPACE_ID)
                    .setProjectId(PROJECT_ID)
                    .build());

            assertFalse(Files.exists(projectDir));
        }

        @Test
        void projectNotFound_returnsNotFound() {
            assertNotFound(() -> stub.deleteProject(DeleteProjectRequest.newBuilder()
                    .setWorkspaceId(WORKSPACE_ID)
                    .setProjectId("missing")
                    .build()));
        }
    }
}
