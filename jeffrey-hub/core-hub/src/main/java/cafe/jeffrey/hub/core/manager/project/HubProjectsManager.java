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

package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.core.volume.ProjectDirectory;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;

import java.util.List;

/**
 * The projects of one workspace: its visible child directories, each known by its directory name
 * — the project name the provisioner was configured with.
 */
public class HubProjectsManager implements ProjectsManager {

    private final WorkspaceDirectory workspace;
    private final ProjectManager.Factory projectManagerFactory;

    public HubProjectsManager(WorkspaceDirectory workspace, ProjectManager.Factory projectManagerFactory) {
        this.workspace = workspace;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public List<ProjectManager> findAll() {
        return projectDirectories().stream()
                .map(projectManagerFactory)
                .toList();
    }

    private List<ProjectDirectory> projectDirectories() {
        return workspace.projects();
    }
}
