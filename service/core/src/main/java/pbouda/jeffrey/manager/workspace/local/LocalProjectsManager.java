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

package pbouda.jeffrey.manager.workspace.local;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.model.CreateProject;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;

import java.util.List;
import java.util.Optional;

public class LocalProjectsManager implements ProjectsManager {

    private final WorkspaceInfo workspaceInfo;

    public LocalProjectsManager(WorkspaceInfo workspaceInfo) {
        this.workspaceInfo = workspaceInfo;
    }

    @Override
    public ProjectManager create(CreateProject createProject) {
        return null;
    }

    @Override
    public List<? extends ProjectManager> findAll() {
        return List.of();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        return Optional.empty();
    }

    @Override
    public Optional<ProjectManager> findByOriginProjectId(String originProjectId) {
        return Optional.empty();
    }
}
