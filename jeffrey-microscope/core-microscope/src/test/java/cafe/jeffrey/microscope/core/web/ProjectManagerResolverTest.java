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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Not found means the remote said so. A remote that cannot be reached is reported as its gRPC
 * failure, which the exception handler turns into "remote unavailable", rather than as a missing
 * workspace or project.
 */
@ExtendWith(MockitoExtension.class)
class ProjectManagerResolverTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";

    @Mock
    RemoteManager remoteManager;

    @Mock
    RemoteConnection remote;

    @Mock
    WorkspaceManager workspace;

    @Mock
    ProjectManager project;

    private ProjectManagerResolver resolver;

    @BeforeEach
    void setUp() {
        when(remoteManager.require()).thenReturn(remote);
        resolver = new ProjectManagerResolver(remoteManager);
    }

    @Nested
    class Project {

        @Test
        void isResolvedInOneRemoteCallWithoutAWorkspaceLookup() {
            when(remote.projectOrThrow(WORKSPACE_ID, PROJECT_ID)).thenReturn(Optional.of(project));

            assertSame(project, resolver.resolve(WORKSPACE_ID, PROJECT_ID));
            verify(remote, never()).workspaceOrThrow(any());
        }

        @Test
        void aProjectTheRemoteDoesNotHoldIsNotFound() {
            when(remote.projectOrThrow(WORKSPACE_ID, PROJECT_ID)).thenReturn(Optional.empty());

            JeffreyClientException e = assertThrows(JeffreyClientException.class,
                    () -> resolver.resolve(WORKSPACE_ID, PROJECT_ID));

            assertEquals(ErrorCode.PROJECT_NOT_FOUND, e.getCode());
        }

        @Test
        void anUnreachableRemoteIsNotReportedAsAMissingProject() {
            when(remote.projectOrThrow(WORKSPACE_ID, PROJECT_ID)).thenThrow(Status.UNAVAILABLE.asRuntimeException());

            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> resolver.resolve(WORKSPACE_ID, PROJECT_ID));

            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    @Nested
    class Workspace {

        @Test
        void aWorkspaceTheRemoteDoesNotHoldIsNotFound() {
            when(remote.workspaceOrThrow(WORKSPACE_ID)).thenReturn(Optional.empty());

            JeffreyClientException e = assertThrows(JeffreyClientException.class,
                    () -> resolver.resolveWorkspace(WORKSPACE_ID));

            assertEquals(ErrorCode.WORKSPACE_NOT_FOUND, e.getCode());
        }

        @Test
        void anUnreachableRemoteIsNotReportedAsAMissingWorkspace() {
            when(remote.workspaceOrThrow(WORKSPACE_ID)).thenThrow(Status.UNAVAILABLE.asRuntimeException());

            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> resolver.resolveWorkspace(WORKSPACE_ID));

            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }

        @Test
        void aWorkspaceTheRemoteHoldsIsResolved() {
            when(remote.workspaceOrThrow(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

            assertSame(workspace, resolver.resolveWorkspace(WORKSPACE_ID));
        }
    }
}
