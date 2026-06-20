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

package cafe.jeffrey.shared.ui.workspace.bridge;

import cafe.jeffrey.shared.ui.workspace.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.workspace.dto.WorkspaceResponse;

import java.util.List;

/**
 * Deployment-agnostic access to a remote hub's workspaces and projects, used by the shared
 * WorkspaceBrowser controllers. Each deployment supplies an implementation that resolves the data
 * its own way — microscope via its local manager objects, the analyst via direct gRPC calls — and
 * maps it into the shared response DTOs so the controllers stay free of deployment-specific types.
 *
 * <p>Implementations may throw {@link io.grpc.StatusRuntimeException} from the mutating operations;
 * the controllers translate those into HTTP errors at the boundary.
 */
public interface WorkspaceBrowserAccess {

    List<WorkspaceResponse> workspaces(String hubId);

    WorkspaceResponse workspace(String hubId, String workspaceId);

    WorkspaceResponse createWorkspace(String hubId, String referenceId, String name);

    void deleteWorkspace(String hubId, String workspaceId);

    List<ProjectResponse> projects(String hubId, String workspaceId, boolean includeDeleted);

    ProjectResponse project(String hubId, String workspaceId, String projectId);
}
