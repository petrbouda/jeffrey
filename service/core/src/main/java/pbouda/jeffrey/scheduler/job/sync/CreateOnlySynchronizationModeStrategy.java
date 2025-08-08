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

package pbouda.jeffrey.scheduler.job.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.model.CreateProject;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import java.util.List;

public class CreateOnlySynchronizationModeStrategy implements SynchronizationModeStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CreateOnlySynchronizationModeStrategy.class);

    private final ProjectsManager projectsManager;

    public CreateOnlySynchronizationModeStrategy(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Override
    public void executeOnWorkspace(
            List<WorkspaceProject> projects,
            List<? extends ProjectManager> projectManagers,
            String templateId) {

        for (WorkspaceProject project : projects) {
            // Has the folder been already a project?
            boolean projectNotExists = projectManagers.stream()
                    .map(manager -> manager.info().id())
                    .noneMatch(projectId -> projectId.equals(project.projectId()));

            if (projectNotExists) {
                String newProjectName = project.projectName();

                CreateProject createProject = new CreateProject(
                        project.projectId(), project.projectName(), project.workspaceId(), templateId);

                projectsManager.create(createProject);
                LOG.info("ProjectsSynchronizer Job created a new project: name={} template_id={}",
                        newProjectName, templateId);
            }
        }
    }

    @Override
    public SynchronizationMode synchronizationMode() {
        return SynchronizationMode.CREATE_ONLY;
    }
}
