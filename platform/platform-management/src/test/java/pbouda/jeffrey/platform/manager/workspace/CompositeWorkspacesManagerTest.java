/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.provider.platform.repository.WorkspacesRepository;
import pbouda.jeffrey.shared.common.model.workspace.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeWorkspacesManagerTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");

    @Mock
    private WorkspacesRepository workspacesRepository;
    @Mock
    private ObjectFactory<SandboxWorkspacesManager> sandboxFactory;
    @Mock
    private ObjectFactory<RemoteWorkspacesManager> remoteFactory;
    @Mock
    private ObjectFactory<LiveWorkspacesManager> liveFactory;

    private CompositeWorkspacesManager manager;

    @BeforeEach
    void setUp() {
        manager = new CompositeWorkspacesManager(workspacesRepository, sandboxFactory, remoteFactory, liveFactory);
    }

    private static WorkspaceInfo workspaceInfo(String id, WorkspaceType type) {
        return new WorkspaceInfo(id, null, null, "Workspace " + id, null,
                null, null, NOW, type, WorkspaceStatus.AVAILABLE, 0);
    }

    @Nested
    class Create {

        @Test
        void delegatesToSandboxManager_forSandboxType() {
            WorkspacesManager.CreateWorkspaceRequest request = WorkspacesManager.CreateWorkspaceRequest.builder()
                    .name("sandbox-ws")
                    .type(WorkspaceType.SANDBOX)
                    .build();

            SandboxWorkspacesManager sandboxMgr = mock(SandboxWorkspacesManager.class);
            WorkspaceInfo expected = workspaceInfo("ws-1", WorkspaceType.SANDBOX);
            when(sandboxFactory.getObject()).thenReturn(sandboxMgr);
            when(sandboxMgr.create(request)).thenReturn(expected);

            WorkspaceInfo result = manager.create(request);

            assertSame(expected, result);
            verify(sandboxFactory).getObject();
        }

        @Test
        void delegatesToLiveManager_forLiveType() {
            WorkspacesManager.CreateWorkspaceRequest request = WorkspacesManager.CreateWorkspaceRequest.builder()
                    .name("live-ws")
                    .type(WorkspaceType.LIVE)
                    .build();

            LiveWorkspacesManager liveMgr = mock(LiveWorkspacesManager.class);
            WorkspaceInfo expected = workspaceInfo("ws-2", WorkspaceType.LIVE);
            when(liveFactory.getObject()).thenReturn(liveMgr);
            when(liveMgr.create(request)).thenReturn(expected);

            WorkspaceInfo result = manager.create(request);

            assertSame(expected, result);
            verify(liveFactory).getObject();
        }

        @Test
        void delegatesToRemoteManager_forRemoteType() {
            WorkspacesManager.CreateWorkspaceRequest request = WorkspacesManager.CreateWorkspaceRequest.builder()
                    .name("remote-ws")
                    .type(WorkspaceType.REMOTE)
                    .build();

            RemoteWorkspacesManager remoteMgr = mock(RemoteWorkspacesManager.class);
            WorkspaceInfo expected = workspaceInfo("ws-3", WorkspaceType.REMOTE);
            when(remoteFactory.getObject()).thenReturn(remoteMgr);
            when(remoteMgr.create(request)).thenReturn(expected);

            WorkspaceInfo result = manager.create(request);

            assertSame(expected, result);
            verify(remoteFactory).getObject();
        }
    }

    @Nested
    class FindById {

        @Test
        void returnsWorkspaceManager_whenFound() {
            WorkspaceInfo info = workspaceInfo("ws-1", WorkspaceType.SANDBOX);
            WorkspaceManager wsManager = mock(WorkspaceManager.class);
            SandboxWorkspacesManager sandboxMgr = mock(SandboxWorkspacesManager.class);

            when(workspacesRepository.findAll()).thenReturn(List.of(info));
            when(sandboxFactory.getObject()).thenReturn(sandboxMgr);
            when(sandboxMgr.mapToWorkspaceManager(info)).thenReturn(wsManager);

            Optional<WorkspaceManager> result = manager.findById("ws-1");

            assertTrue(result.isPresent());
            assertSame(wsManager, result.get());
        }

        @Test
        void returnsEmpty_whenNotFound() {
            when(workspacesRepository.findAll()).thenReturn(List.of());

            Optional<WorkspaceManager> result = manager.findById("ws-missing");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class MapToWorkspaceManager {

        @Test
        void routesToCorrectManager_basedOnWorkspaceType() {
            WorkspaceInfo sandboxInfo = workspaceInfo("ws-1", WorkspaceType.SANDBOX);
            WorkspaceInfo liveInfo = workspaceInfo("ws-2", WorkspaceType.LIVE);
            WorkspaceInfo remoteInfo = workspaceInfo("ws-3", WorkspaceType.REMOTE);

            SandboxWorkspacesManager sandboxMgr = mock(SandboxWorkspacesManager.class);
            LiveWorkspacesManager liveMgr = mock(LiveWorkspacesManager.class);
            RemoteWorkspacesManager remoteMgr = mock(RemoteWorkspacesManager.class);

            when(sandboxFactory.getObject()).thenReturn(sandboxMgr);
            when(liveFactory.getObject()).thenReturn(liveMgr);
            when(remoteFactory.getObject()).thenReturn(remoteMgr);

            manager.mapToWorkspaceManager(sandboxInfo);
            manager.mapToWorkspaceManager(liveInfo);
            manager.mapToWorkspaceManager(remoteInfo);

            verify(sandboxMgr).mapToWorkspaceManager(sandboxInfo);
            verify(liveMgr).mapToWorkspaceManager(liveInfo);
            verify(remoteMgr).mapToWorkspaceManager(remoteInfo);
        }
    }
}
