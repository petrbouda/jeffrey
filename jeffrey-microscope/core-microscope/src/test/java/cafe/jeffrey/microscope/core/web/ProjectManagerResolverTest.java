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

import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Not found means the hub said so. A hub that cannot be reached is reported as its gRPC failure,
 * which the exception handler turns into "hub unavailable", rather than as a missing workspace or
 * project.
 */
@ExtendWith(MockitoExtension.class)
class ProjectManagerResolverTest {

    private static final String HUB_ID = "hub-1";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";

    @Mock
    HubsManager hubsManager;

    @Mock
    HubManager hub;

    @Mock
    WorkspaceManager workspace;

    @Mock
    ProjectsManager projectsManager;

    private ProjectManagerResolver resolver;

    @BeforeEach
    void setUp() {
        when(hubsManager.findById(HUB_ID)).thenReturn(Optional.of(hub));
        resolver = new ProjectManagerResolver(hubsManager);
    }

    @Test
    void anUnreachableHubIsNotReportedAsAMissingWorkspace() {
        when(hub.workspaceOrThrow(WORKSPACE_ID)).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> resolver.resolve(HUB_ID, WORKSPACE_ID, PROJECT_ID));

        assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
    }

    @Test
    void aWorkspaceTheHubDoesNotHoldIsNotFound() {
        when(hub.workspaceOrThrow(WORKSPACE_ID)).thenReturn(Optional.empty());

        JeffreyClientException e = assertThrows(JeffreyClientException.class,
                () -> resolver.resolve(HUB_ID, WORKSPACE_ID, PROJECT_ID));

        assertEquals(ErrorCode.WORKSPACE_NOT_FOUND, e.getCode());
    }

    @Test
    void aProjectTheHubDoesNotHoldIsNotFound() {
        when(hub.workspaceOrThrow(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspace.projectsManager()).thenReturn(projectsManager);
        when(projectsManager.projectOrThrow(PROJECT_ID)).thenReturn(Optional.empty());

        JeffreyClientException e = assertThrows(JeffreyClientException.class,
                () -> resolver.resolve(HUB_ID, WORKSPACE_ID, PROJECT_ID));

        assertEquals(ErrorCode.PROJECT_NOT_FOUND, e.getCode());
    }
}
