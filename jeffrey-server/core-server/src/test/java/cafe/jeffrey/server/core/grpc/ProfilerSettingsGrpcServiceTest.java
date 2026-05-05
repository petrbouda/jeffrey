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

package cafe.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.manager.ProfilerSettingsManager;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.persistence.api.ProfilerRepository;
import cafe.jeffrey.server.persistence.api.ProjectRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

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

    private Server server;
    private ManagedChannel channel;

    private ProfilerSettingsServiceGrpc.ProfilerSettingsServiceBlockingStub startServer(
            ProfilerSettingsGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return ProfilerSettingsServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ========== GetSettings ==========

    @Nested
    class GetSettings {

        @Test
        void returnsProjectLevelSettings() throws Exception {
            var settingsManager = mock(ProfilerSettingsManager.class);
            when(settingsManager.fetchEffectiveSettings()).thenReturn(
                    new EffectiveProfilerSettings(AGENT_SETTINGS, EffectiveProfilerSettings.SettingsLevel.PROJECT));

            var stub = startServer(serviceWithProject(settingsManager));

            GetProfilerSettingsResponse response = stub.getSettings(
                    GetProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(AGENT_SETTINGS, response.getAgentSettings());
            assertEquals(SettingsLevel.SETTINGS_LEVEL_PROJECT, response.getLevel());
        }

        @Test
        void returnsGlobalLevelSettings() throws Exception {
            var settingsManager = mock(ProfilerSettingsManager.class);
            when(settingsManager.fetchEffectiveSettings()).thenReturn(
                    new EffectiveProfilerSettings("global-settings", EffectiveProfilerSettings.SettingsLevel.GLOBAL));

            var stub = startServer(serviceWithProject(settingsManager));

            GetProfilerSettingsResponse response = stub.getSettings(
                    GetProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals("global-settings", response.getAgentSettings());
            assertEquals(SettingsLevel.SETTINGS_LEVEL_GLOBAL, response.getLevel());
        }

        @Test
        void returnsNoneLevelWhenNoSettings() throws Exception {
            var settingsManager = mock(ProfilerSettingsManager.class);
            when(settingsManager.fetchEffectiveSettings()).thenReturn(
                    EffectiveProfilerSettings.none());

            var stub = startServer(serviceWithProject(settingsManager));

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
            var settingsManager = mock(ProfilerSettingsManager.class);
            var stub = startServer(serviceWithProject(settingsManager));

            stub.upsertSettings(
                    UpsertProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .setAgentSettings(AGENT_SETTINGS)
                            .build());

            verify(settingsManager).upsertSettings(AGENT_SETTINGS);
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
            var settingsManager = mock(ProfilerSettingsManager.class);
            var stub = startServer(serviceWithProject(settingsManager));

            stub.deleteSettings(
                    DeleteProfilerSettingsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            verify(settingsManager).deleteSettings();
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

    // ========== ListAllSettings ==========

    @Nested
    class ListAllSettings {

        @Test
        void returnsAllSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findAllSettings()).thenReturn(List.of(
                    new ProfilerInfo(null, null, "global-settings"),
                    new ProfilerInfo(WORKSPACE_ID, null, "workspace-settings"),
                    new ProfilerInfo(WORKSPACE_ID, PROJECT_ID, AGENT_SETTINGS)
            ));

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            ListAllProfilerSettingsResponse response = stub.listAllSettings(
                    ListAllProfilerSettingsRequest.getDefaultInstance());

            assertEquals(3, response.getSettingsCount());

            ProfilerSettingsEntry global = response.getSettings(0);
            assertEquals("", global.getWorkspaceId());
            assertEquals("", global.getProjectId());
            assertEquals("global-settings", global.getAgentSettings());

            ProfilerSettingsEntry workspace = response.getSettings(1);
            assertEquals(WORKSPACE_ID, workspace.getWorkspaceId());
            assertEquals("", workspace.getProjectId());
            assertEquals("workspace-settings", workspace.getAgentSettings());

            ProfilerSettingsEntry project = response.getSettings(2);
            assertEquals(WORKSPACE_ID, project.getWorkspaceId());
            assertEquals(PROJECT_ID, project.getProjectId());
            assertEquals(AGENT_SETTINGS, project.getAgentSettings());
        }

        @Test
        void returnsEmptyListWhenNoSettings() throws Exception {
            var profilerRepo = mock(ProfilerRepository.class);
            when(profilerRepo.findAllSettings()).thenReturn(List.of());

            var stub = startServer(serviceWithProfilerRepository(profilerRepo));

            ListAllProfilerSettingsResponse response = stub.listAllSettings(
                    ListAllProfilerSettingsRequest.getDefaultInstance());

            assertEquals(0, response.getSettingsCount());
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

    private static final cafe.jeffrey.shared.common.model.ProjectInfo TEST_PROJECT_INFO =
            new cafe.jeffrey.shared.common.model.ProjectInfo(
                    PROJECT_ID, null, null, null, null, null, null, null, null, null);

    /**
     * Creates a service where findProject(PROJECT_ID) succeeds and returns the given settingsManager.
     */
    private ProfilerSettingsGrpcService serviceWithProject(ProfilerSettingsManager settingsManager) {
        var projectManager = mock(ProjectManager.class);
        when(projectManager.profilerSettingsManager()).thenReturn(settingsManager);

        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);
        when(platformRepositories.newProfilerRepository()).thenReturn(mock(ProfilerRepository.class));

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        when(projectManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(projectManager);

        return new ProfilerSettingsGrpcService(platformRepositories, projectManagerFactory);
    }

    /**
     * Creates a service where findProject("non-existent") fails (project not found).
     */
    private ProfilerSettingsGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(projectRepo);
        when(platformRepositories.newProfilerRepository()).thenReturn(mock(ProfilerRepository.class));

        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProfilerSettingsGrpcService(platformRepositories, projectManagerFactory);
    }

    /**
     * Creates a service with a specific ProfilerRepository (for list/upsert/delete at level tests).
     */
    private ProfilerSettingsGrpcService serviceWithProfilerRepository(ProfilerRepository profilerRepo) {
        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProfilerRepository()).thenReturn(profilerRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProfilerSettingsGrpcService(platformRepositories, projectManagerFactory);
    }
}
