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

package pbouda.jeffrey.scheduler.task.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.scheduler.task.model.SynchronizationMode;

import java.nio.file.Path;
import java.util.List;

public class CreateOnlySynchronizationModeStrategy implements SynchronizationModeStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CreateOnlySynchronizationModeStrategy.class);

    private final ProjectsManager projectsManager;

    public CreateOnlySynchronizationModeStrategy(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Override
    public void execute(List<Path> folders, List<? extends ProjectManager> projects, String templateId) {
        for (Path folder : folders) {
            // Has the folder been already a project?
            boolean projectNotExists = projects.stream()
                    .map(project -> project.info().name())
                    .noneMatch(name -> name.equals(folder.getFileName().toString()));

            if (projectNotExists) {
                String newProjectName = folder.getFileName().toString();
                projectsManager.create(newProjectName, templateId, ExternalProjectLink.byProjectsSynchronizer(folder));
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
