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

import cafe.jeffrey.hub.api.v1.ConfigScope;
import cafe.jeffrey.hub.api.v1.ConfigScopeKey;
import cafe.jeffrey.hub.api.v1.ConfigType;
import cafe.jeffrey.hub.api.v1.DeleteConfigRequest;
import cafe.jeffrey.hub.api.v1.GetConfigRequest;
import cafe.jeffrey.hub.api.v1.GetConfigResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsResponse;
import cafe.jeffrey.hub.api.v1.ScopedConfigServiceGrpc;
import cafe.jeffrey.hub.api.v1.UpsertConfigRequest;
import cafe.jeffrey.hub.api.v1.UpsertConfigResponse;
import cafe.jeffrey.hub.core.config.ScopedConfigManager;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.config.ScopedConfig;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.persistence.api.WorkspacesRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The proto and the domain both call an enum {@code ConfigType}, so the domain one is spelled out
 * where both appear — the same concession {@code ProtoMappers} makes for the same reason.
 */
class ScopedConfigGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final String COMMAND = "-agentpath:/opt/lib.so=start,cpu";

    private InProcessGrpcServer server;

    @AfterEach
    void shutdown() {
        if (server != null) {
            server.close();
        }
    }

    private ScopedConfigServiceGrpc.ScopedConfigServiceBlockingStub startServer(ScopedConfigGrpcService service) {
        server = InProcessGrpcServer.start(service);
        return ScopedConfigServiceGrpc.newBlockingStub(server.channel());
    }

    @Nested
    class Reading {

        @Test
        void getReturnsWhatTheScopeHolds() {
            ScopedConfigManager manager = manager();
            when(manager.find(any())).thenReturn(config(ScopedConfigKey.workspace(WORKSPACE_ID), COMMAND));

            GetConfigResponse response = startServer(service(manager))
                    .getConfig(GetConfigRequest.newBuilder().setKey(workspaceKey()).build());

            assertEquals(1, response.getConfig().getEntriesCount());
            assertEquals(COMMAND, response.getConfig().getEntries(0).getValue());
            assertEquals(ConfigType.CONFIG_TYPE_ASPROF_SETTINGS, response.getConfig().getEntries(0).getType());
        }

        /** An empty scope is a normal answer, not a missing resource. */
        @Test
        void getOnAnEmptyScopeSucceedsWithNoEntries() {
            ScopedConfigManager manager = manager();
            when(manager.find(any())).thenReturn(ScopedConfig.empty(ScopedConfigKey.workspace(WORKSPACE_ID)));

            GetConfigResponse response = startServer(service(manager))
                    .getConfig(GetConfigRequest.newBuilder().setKey(workspaceKey()).build());

            assertEquals(0, response.getConfig().getEntriesCount());
            assertEquals("", response.getConfig().getDigest());
        }

        @Test
        void listReturnsEveryScopeThatAppliesToAWorkspace() {
            ScopedConfigManager manager = manager();
            when(manager.findForWorkspace(WORKSPACE_ID)).thenReturn(List.of(
                    config(ScopedConfigKey.global(), "global"),
                    config(ScopedConfigKey.workspace(WORKSPACE_ID), "workspace")));

            ListWorkspaceConfigsResponse response = startServer(service(manager)).listWorkspaceConfigs(
                    ListWorkspaceConfigsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(2, response.getConfigsCount());
            assertEquals(ConfigScope.CONFIG_SCOPE_GLOBAL, response.getConfigs(0).getKey().getScope());
            assertEquals(ConfigScope.CONFIG_SCOPE_WORKSPACE, response.getConfigs(1).getKey().getScope());
        }

        @Test
        void listWithoutAWorkspaceIsInvalidArgument() {
            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(service(manager())).listWorkspaceConfigs(
                            ListWorkspaceConfigsRequest.getDefaultInstance()));

            assertEquals(Status.Code.INVALID_ARGUMENT, thrown.getStatus().getCode());
        }
    }

    @Nested
    class Writing {

        @Test
        void upsertStoresTheValueAndReturnsTheNewDigest() {
            ScopedConfigManager manager = manager();
            when(manager.upsert(any(), any(), anyString()))
                    .thenReturn(config(ScopedConfigKey.workspace(WORKSPACE_ID), COMMAND));

            UpsertConfigResponse response = startServer(service(manager)).upsertConfig(
                    UpsertConfigRequest.newBuilder()
                            .setKey(workspaceKey())
                            .setType(ConfigType.CONFIG_TYPE_ASPROF_SETTINGS)
                            .setValue(COMMAND)
                            .build());

            assertEquals("digest", response.getConfig().getDigest());
            verify(manager).upsert(
                    ScopedConfigKey.workspace(WORKSPACE_ID),
                    cafe.jeffrey.shared.common.config.ConfigType.ASPROF_SETTINGS,
                    COMMAND);
        }

        @Test
        void deleteRemovesTheValue() {
            ScopedConfigManager manager = manager();
            when(manager.delete(any(), any()))
                    .thenReturn(ScopedConfig.empty(ScopedConfigKey.workspace(WORKSPACE_ID)));

            startServer(service(manager)).deleteConfig(DeleteConfigRequest.newBuilder()
                    .setKey(workspaceKey())
                    .setType(ConfigType.CONFIG_TYPE_ASPROF_SETTINGS)
                    .build());

            verify(manager).delete(
                    ScopedConfigKey.workspace(WORKSPACE_ID),
                    cafe.jeffrey.shared.common.config.ConfigType.ASPROF_SETTINGS);
        }

        /** The validator's message is the whole point of the status: it is what the editor shows. */
        @Test
        void aValueTheTypeRejectsIsInvalidArgumentAndStoresNothing() {
            ScopedConfigManager manager = manager();
            when(manager.upsert(any(), any(), anyString()))
                    .thenThrow(new IllegalArgumentException("The profiler command must not be empty"));

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(service(manager)).upsertConfig(UpsertConfigRequest.newBuilder()
                            .setKey(workspaceKey())
                            .setType(ConfigType.CONFIG_TYPE_ASPROF_SETTINGS)
                            .setValue("")
                            .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, thrown.getStatus().getCode());
            assertTrue(thrown.getStatus().getDescription().contains("must not be empty"));
        }
    }

    @Nested
    class RejectedKeys {

        /**
         * proto3 hands an unknown enum value over as the zero constant, so a type this hub does not
         * publish must be refused rather than silently stored as whichever one is listed first.
         */
        @Test
        void anUnspecifiedTypeIsRefusedRatherThanDefaulted() {
            ScopedConfigManager manager = manager();

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(service(manager)).upsertConfig(UpsertConfigRequest.newBuilder()
                            .setKey(workspaceKey())
                            .setValue(COMMAND)
                            .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, thrown.getStatus().getCode());
            verify(manager, never()).upsert(any(), any(), anyString());
        }

        @Test
        void anUnspecifiedScopeIsRefused() {
            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(service(manager())).getConfig(GetConfigRequest.newBuilder()
                            .setKey(ConfigScopeKey.getDefaultInstance())
                            .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, thrown.getStatus().getCode());
        }

        /** A workspace scope carrying a project id is a caller bug, not a project scope. */
        @Test
        void idsThatDoNotMatchTheScopeAreRefused() {
            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(service(manager())).getConfig(GetConfigRequest.newBuilder()
                            .setKey(ConfigScopeKey.newBuilder()
                                    .setScope(ConfigScope.CONFIG_SCOPE_WORKSPACE)
                                    .setWorkspaceId(WORKSPACE_ID)
                                    .setProjectId(PROJECT_ID)
                                    .build())
                            .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, thrown.getStatus().getCode());
        }

        @Test
        void anUnknownWorkspaceIsNotFound() {
            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> startServer(serviceWithNoWorkspace()).getConfig(
                            GetConfigRequest.newBuilder().setKey(workspaceKey()).build()));

            assertEquals(Status.Code.NOT_FOUND, thrown.getStatus().getCode());
        }
    }

    private static ConfigScopeKey workspaceKey() {
        return ConfigScopeKey.newBuilder()
                .setScope(ConfigScope.CONFIG_SCOPE_WORKSPACE)
                .setWorkspaceId(WORKSPACE_ID)
                .build();
    }

    private static ScopedConfig config(ScopedConfigKey key, String value) {
        return new ScopedConfig(
                key,
                List.of(new ScopedConfigEntry(
                        key, cafe.jeffrey.shared.common.config.ConfigType.ASPROF_SETTINGS, value, NOW)),
                "digest");
    }

    private static ScopedConfigManager manager() {
        return mock(ScopedConfigManager.class);
    }

    private static ScopedConfigGrpcService service(ScopedConfigManager manager) {
        return new ScopedConfigGrpcService(manager, lookupsWithWorkspace());
    }

    private static ScopedConfigGrpcService serviceWithNoWorkspace() {
        HubPlatformRepositories repositories = mock(HubPlatformRepositories.class);
        WorkspacesRepository workspaces = mock(WorkspacesRepository.class);
        when(repositories.newWorkspacesRepository()).thenReturn(workspaces);
        when(workspaces.find(anyString())).thenReturn(Optional.empty());

        return new ScopedConfigGrpcService(manager(), new GrpcLookups(repositories, null, null));
    }

    private static GrpcLookups lookupsWithWorkspace() {
        HubPlatformRepositories repositories = mock(HubPlatformRepositories.class);
        WorkspacesRepository workspaces = mock(WorkspacesRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);

        when(repositories.newWorkspacesRepository()).thenReturn(workspaces);
        when(repositories.newProjectRepository(anyString())).thenReturn(projects);
        when(workspaces.find(anyString())).thenReturn(Optional.of(new WorkspaceInfo(
                WORKSPACE_ID, WORKSPACE_ID, WORKSPACE_ID, "Workspace", null, null,
                NOW, WorkspaceStatus.AVAILABLE, 0)));
        when(projects.find()).thenReturn(Optional.of(new ProjectInfo(
                PROJECT_ID, PROJECT_ID, "Project", "Label", null, WORKSPACE_ID, NOW, NOW, Map.of(), null)));

        return new GrpcLookups(repositories, null, null);
    }
}
