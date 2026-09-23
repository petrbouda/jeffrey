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

package cafe.jeffrey.shared.ui.hub.bridge;

import cafe.jeffrey.shared.ui.hub.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.hub.dto.WorkspaceResponse;

import java.util.List;

/**
 * Deployment-agnostic access to a remote hub's workspaces and projects, used by the shared
 * WorkspaceBrowser controllers. Each deployment supplies an implementation that resolves the data
 * its own way — microscope via its local manager objects — and maps it into the shared response
 * DTOs so the controllers stay free of deployment-specific types.
 *
 * <p>Implementations may throw {@link io.grpc.StatusRuntimeException} from the mutating operations;
 * the controllers translate those into HTTP errors at the boundary.
 */
public interface HubBrowserAccess {

    List<WorkspaceResponse> workspaces(String hubId);

    WorkspaceResponse workspace(String hubId, String workspaceId);

    WorkspaceResponse createWorkspace(String hubId, String referenceId, String name);

    void deleteWorkspace(String hubId, String workspaceId);

    List<ProjectResponse> projects(String hubId, String workspaceId, boolean includeDeleted);

    ProjectResponse project(String hubId, String workspaceId, String projectId);
}
