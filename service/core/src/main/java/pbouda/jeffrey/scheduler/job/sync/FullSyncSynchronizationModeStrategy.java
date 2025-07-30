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
import pbouda.jeffrey.common.model.ExternalComponentType;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;

import java.nio.file.Path;
import java.util.List;

public class FullSyncSynchronizationModeStrategy extends CreateOnlySynchronizationModeStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(FullSyncSynchronizationModeStrategy.class);

    public FullSyncSynchronizationModeStrategy(ProjectsManager projectsManager) {
        super(projectsManager);
    }

    @Override
    public void execute(List<Path> folders, List<? extends ProjectManager> projects, String templateId) {
        super.execute(folders, projects, templateId);

        // Remove projects that are not in the watched folder
        for (ProjectManager project : projects) {
            ExternalProjectLink projectLink = project.info().externalLink();
            if (projectLink == null || projectLink.externalComponentType() != ExternalComponentType.GLOBAL_JOB) {
                // Skip the project because it is not managed by this job
                continue;
            }

            String projectName = project.info().name();
            if (folders.stream().noneMatch(folder -> folder.getFileName().toString().equals(projectName))) {
                project.delete();
                LOG.info("ProjectsSynchronizer Job removed project: name={}", projectName);
            }
        }
    }

    @Override
    public SynchronizationMode synchronizationMode() {
        return SynchronizationMode.FULL_SYNC;
    }
}
