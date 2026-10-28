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

package cafe.jeffrey.shared.ui.remote.bridge;

import cafe.jeffrey.shared.ui.remote.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.remote.dto.WorkspaceResponse;

import java.util.List;

/**
 * Access to the remote's workspaces and projects, used by the shared WorkspaceBrowser controllers.
 * Microscope supplies the implementation over its manager objects and maps the data into the shared
 * response DTOs, so the controllers stay free of Microscope's own types.
 */
public interface RemoteBrowserAccess {

    List<WorkspaceResponse> workspaces();

    WorkspaceResponse workspace(String workspaceId);

    WorkspaceResponse createWorkspace(String referenceId);

    void deleteWorkspace(String workspaceId);

    List<ProjectResponse> projects(String workspaceId);

    ProjectResponse project(String workspaceId, String projectId);
}
