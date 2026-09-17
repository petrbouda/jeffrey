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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.persistence.api.ProfilerRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import java.time.Instant;
import cafe.jeffrey.hub.persistence.api.WorkspacesRepository;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.model.ProfilerInfo;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfilerSettingsGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String AGENT_SETTINGS = "start,event=cpu,interval=10ms";

    private InProcessGrpcServer grpc;

    private ProfilerSettingsServiceGrpc.ProfilerSettingsServiceBlockingStub startServer(
            ProfilerSettingsGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return ProfilerSettingsServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    // ========== GetSettings ==========

    @Nested
    class GetSettings {

        @Test
        void returnsProjectLevelSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.fetchProfilerSettings(WORKSPACE_ID, PROJECT_ID)).thenReturn(List.of(
                    new ProfilerInfo(WORKSPACE_ID, PROJECT_ID, AGENT_SETTINGS),
                    new ProfilerInfo(null, null, "global-settings")));

            var stub = startServer(serviceWithProject(profilerRepo));

            GetProfilerSettingsResponse response = stub.getSettings(
                    GetProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(AGENT_SETTINGS, response.getAgentSettings());
            assertEquals(SettingsLevel.SETTINGS_LEVEL_PROJECT, response.getLevel());
        }

        @Test
        void returnsGlobalLevelSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.fetchProfilerSettings(WORKSPACE_ID, PROJECT_ID)).thenReturn(List.of(
                    new ProfilerInfo(null, null, "global-settings")));

            var stub = startServer(serviceWithProject(profilerRepo));

            GetProfilerSettingsResponse response = stub.getSettings(
                    GetProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals("global-settings", response.getAgentSettings());
            assertEquals(SettingsLevel.SETTINGS_LEVEL_GLOBAL, response.getLevel());
        }

        @Test
        void returnsNoneLevelWhenNoSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.fetchProfilerSettings(WORKSPACE_ID, PROJECT_ID)).thenReturn(List.of());

            var stub = startServer(serviceWithProject(profilerRepo));

            GetProfilerSettingsResponse response = stub.getSettings(
                    GetProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals("", response.getAgentSettings());
            assertEquals(SettingsLevel.SETTINGS_LEVEL_UNSPECIFIED, response.getLevel());
        }

        @Test
        void projectNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoProject());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getSettings(
                            GetProfilerSettingsRequest.newBuilder()
                                    .setProjectId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== UpsertSettings ==========

    @Nested
    class UpsertSettings {

        @Test
        void savesSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProject(profilerRepo));

            stub.upsertSettings(
                    UpsertProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .setAgentSettings(AGENT_SETTINGS)
                            .build());

            verify(profilerRepo).upsertSettings(new ProfilerInfo(WORKSPACE_ID, PROJECT_ID, AGENT_SETTINGS));
        }

        @Test
        void projectNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoProject());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.upsertSettings(
                            UpsertProfilerSettingsRequest.newBuilder()
                                    .setProjectId("non-existent")
                                    .setAgentSettings(AGENT_SETTINGS)
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== DeleteSettings ==========

    @Nested
    class DeleteSettings {

        @Test
        void deletesSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProject(profilerRepo));

            stub.deleteSettings(
                    DeleteProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            verify(profilerRepo).deleteSettings(WORKSPACE_ID, PROJECT_ID);
        }

        @Test
        void projectNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoProject());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteSettings(
                            DeleteProfilerSettingsRequest.newBuilder()
                                    .setProjectId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== UpsertSettingsAtLevel ==========

    @Nested
    class UpsertSettingsAtLevel {

        @Test
        void upsertsGlobalSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.upsertSettingsAtLevel(
                    UpsertProfilerSettingsAtLevelRequest.newBuilder()
                            .setAgentSettings("global-settings")
                            .build());

            verify(profilerRepo).upsertSettings(new ProfilerInfo(null, null, "global-settings"));
        }

        @Test
        void upsertsWorkspaceSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.upsertSettingsAtLevel(
                    UpsertProfilerSettingsAtLevelRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setAgentSettings("workspace-settings")
                            .build());

            verify(profilerRepo).upsertSettings(new ProfilerInfo(WORKSPACE_ID, null, "workspace-settings"));
        }

        @Test
        void upsertsProjectSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.upsertSettingsAtLevel(
                    UpsertProfilerSettingsAtLevelRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId(PROJECT_ID)
                            .setAgentSettings(AGENT_SETTINGS)
                            .build());

            verify(profilerRepo).upsertSettings(new ProfilerInfo(WORKSPACE_ID, PROJECT_ID, AGENT_SETTINGS));
        }

        @Test
        void projectIdWithoutWorkspaceId_returnsInvalidArgument() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.upsertSettingsAtLevel(
                            UpsertProfilerSettingsAtLevelRequest.newBuilder()
                                    .setProjectId(PROJECT_ID)
                                    .setAgentSettings(AGENT_SETTINGS)
                                    .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }
    }

    @Nested
    class SettingsAtLevelScopeValidation {

        /**
         * A mistyped id used to write a row that no reader would ever find — the effective
         * settings are resolved from the project's real workspace, not from whatever id the
         * row carries.
         */
        @Test
        void unknownWorkspace_returnsNotFound() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.upsertSettingsAtLevel(
                            UpsertProfilerSettingsAtLevelRequest.newBuilder()
                                    .setWorkspaceId("ghost")
                                    .setAgentSettings(AGENT_SETTINGS)
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }

        @Test
        void unknownProject_returnsNotFound() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteSettingsAtLevel(
                            DeleteProfilerSettingsAtLevelRequest.newBuilder()
                                    .setWorkspaceId(WORKSPACE_ID)
                                    .setProjectId("ghost")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }

        @Test
        void projectOfAnotherWorkspace_returnsNotFound() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var service = serviceWithProfilerRepository(profilerRepo);
            var stub = startServer(service);

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.upsertSettingsAtLevel(
                            UpsertProfilerSettingsAtLevelRequest.newBuilder()
                                    .setWorkspaceId("other-workspace")
                                    .setProjectId(PROJECT_ID)
                                    .setAgentSettings(AGENT_SETTINGS)
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }
    }

    // ========== DeleteSettingsAtLevel ==========

    @Nested
    class DeleteSettingsAtLevel {

        @Test
        void deletesGlobalSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.deleteSettingsAtLevel(
                    DeleteProfilerSettingsAtLevelRequest.getDefaultInstance());

            verify(profilerRepo).deleteSettings(null, null);
        }

        @Test
        void deletesWorkspaceSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.deleteSettingsAtLevel(
                    DeleteProfilerSettingsAtLevelRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .build());

            verify(profilerRepo).deleteSettings(WORKSPACE_ID, null);
        }

        @Test
        void deletesProjectSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            stub.deleteSettingsAtLevel(
                    DeleteProfilerSettingsAtLevelRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId(PROJECT_ID)
                            .build());

            verify(profilerRepo).deleteSettings(WORKSPACE_ID, PROJECT_ID);
        }

        @Test
        void projectIdWithoutWorkspaceId_returnsInvalidArgument() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteSettingsAtLevel(
                            DeleteProfilerSettingsAtLevelRequest.newBuilder()
                                    .setProjectId(PROJECT_ID)
                                    .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }
    }

    // ========== GetWorkspaceEffectiveSettings ==========

    @Nested
    class GetWorkspaceEffectiveSettings {

        @Test
        void returnsBothLevelsWhenSet() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findWorkspaceSettings(WORKSPACE_ID)).thenReturn(List.of(
                    new ProfilerInfo(WORKSPACE_ID, null, AGENT_SETTINGS),
                    new ProfilerInfo(null, null, "global=settings")));

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            GetWorkspaceEffectiveSettingsResponse response = stub.getWorkspaceEffectiveSettings(
                    GetWorkspaceEffectiveSettingsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .build());

            assertTrue(response.hasWorkspaceAgentSettings());
            assertEquals(AGENT_SETTINGS, response.getWorkspaceAgentSettings());
            assertTrue(response.hasGlobalAgentSettings());
            assertEquals("global=settings", response.getGlobalAgentSettings());
        }

        @Test
        void returnsOnlyWorkspaceLevel() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findWorkspaceSettings(WORKSPACE_ID)).thenReturn(List.of(
                    new ProfilerInfo(WORKSPACE_ID, null, AGENT_SETTINGS)));

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            GetWorkspaceEffectiveSettingsResponse response = stub.getWorkspaceEffectiveSettings(
                    GetWorkspaceEffectiveSettingsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .build());

            assertTrue(response.hasWorkspaceAgentSettings());
            assertEquals(AGENT_SETTINGS, response.getWorkspaceAgentSettings());
            assertFalse(response.hasGlobalAgentSettings());
        }

        @Test
        void returnsOnlyGlobalLevel() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findWorkspaceSettings(WORKSPACE_ID)).thenReturn(List.of(
                    new ProfilerInfo(null, null, "global=settings")));

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            GetWorkspaceEffectiveSettingsResponse response = stub.getWorkspaceEffectiveSettings(
                    GetWorkspaceEffectiveSettingsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .build());

            assertFalse(response.hasWorkspaceAgentSettings());
            assertTrue(response.hasGlobalAgentSettings());
            assertEquals("global=settings", response.getGlobalAgentSettings());
        }

        @Test
        void returnsNothingWhenEmpty() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findWorkspaceSettings(WORKSPACE_ID)).thenReturn(List.of());

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            GetWorkspaceEffectiveSettingsResponse response = stub.getWorkspaceEffectiveSettings(
                    GetWorkspaceEffectiveSettingsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .build());

            assertFalse(response.hasWorkspaceAgentSettings());
            assertFalse(response.hasGlobalAgentSettings());
        }

        @Test
        void blankWorkspaceId_returnsInvalidArgument() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getWorkspaceEffectiveSettings(
                            GetWorkspaceEffectiveSettingsRequest.newBuilder().build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
            verifyNoInteractions(profilerRepo);
        }
    }

    // ========== Helpers ==========

    /**
     * Creates a service where PROJECT_ID resolves to a project of WORKSPACE_ID and settings
     * are read from and written to the given repository.
     */
    private ProfilerSettingsGrpcService serviceWithProject(ProfilerRepository profilerRepo) {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(PROJECT_IN_WORKSPACE));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        return new ProfilerSettingsGrpcService(profilerRepo, new GrpcLookups(platformRepositories, null, null));
    }

    /**
     * Creates a service where findProject("non-existent") fails (project not found).
     */
    private ProfilerSettingsGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(projectRepo);

        return new ProfilerSettingsGrpcService(mock(ProfilerRepository.class), new GrpcLookups(platformRepositories, null, null));
    }

    private static final WorkspaceInfo TEST_WORKSPACE_INFO = new WorkspaceInfo(
            WORKSPACE_ID, WORKSPACE_ID, WORKSPACE_ID, "Workspace",
            null, null, Instant.parse("2026-01-01T00:00:00Z"), WorkspaceStatus.AVAILABLE, 1);

    private static final cafe.jeffrey.hub.model.ProjectInfo PROJECT_IN_WORKSPACE =
            new cafe.jeffrey.hub.model.ProjectInfo(
                    PROJECT_ID, null, null, null, null, WORKSPACE_ID, null, null, null, null);

    /**
     * Creates a service with a specific ProfilerRepository (for list/upsert/delete at level tests),
     * where WORKSPACE_ID exists and PROJECT_ID is one of its projects; any other id is unknown.
     */
    private ProfilerSettingsGrpcService serviceWithProfilerRepository(ProfilerRepository profilerRepo) {
        var workspacesRepo = mock(WorkspacesRepository.class);
        when(workspacesRepo.find(WORKSPACE_ID)).thenReturn(Optional.of(TEST_WORKSPACE_INFO));

        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(PROJECT_IN_WORKSPACE));
        var unknownProjectRepo = mock(ProjectRepository.class);

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newWorkspacesRepository()).thenReturn(workspacesRepo);
        when(platformRepositories.newProjectRepository(any())).thenReturn(unknownProjectRepo);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        return new ProfilerSettingsGrpcService(profilerRepo, new GrpcLookups(platformRepositories, null, null));
    }
}
