/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.ProjectInfo;

import java.util.List;
import java.util.Optional;

public class ProjectsManagerImpl implements ProjectsManager {

    private final HomeDirs homeDirs;
    private final ProjectManager.Factory projectManagerFactory;

    public ProjectsManagerImpl(
            HomeDirs homeDirs,
            ProjectManager.Factory projectManagerFactory) {
        this.homeDirs = homeDirs;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectManager create(ProjectInfo projectInfo) {
        return projectManagerFactory.apply(projectInfo)
                .initialize();
    }

    @Override
    public List<? extends ProjectManager> allProjects() {
        return homeDirs.allProjects().stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public List<ProjectInfo> allProjectInfos() {
        return homeDirs.allProjects();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        ProjectInfo projectInfo = homeDirs.project(projectId).readInfo();
        return Optional.ofNullable(projectManagerFactory.apply(projectInfo));
    }

    @Override
    public void delete(String projectId) {
        project(projectId).ifPresent(ProjectManager::cleanup);
    }
}
