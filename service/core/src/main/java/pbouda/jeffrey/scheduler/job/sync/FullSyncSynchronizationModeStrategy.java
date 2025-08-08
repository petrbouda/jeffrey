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
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import java.util.List;

public class FullSyncSynchronizationModeStrategy extends CreateOnlySynchronizationModeStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(FullSyncSynchronizationModeStrategy.class);

    public FullSyncSynchronizationModeStrategy(ProjectsManager projectsManager) {
        super(projectsManager);
    }

    @Override
    public void executeOnWorkspace(
            List<WorkspaceProject> projects,
            List<? extends ProjectManager> projectManagers,
            String templateId) {

        super.executeOnWorkspace(projects, projectManagers, templateId);

        // Remove projects that are not in the watched folder
        for (ProjectManager project : projectManagers) {
            if (project.info().workspaceId() == null) {
                // Skip the project because it is not managed by this job
                continue;
            }

            String projectId = project.info().id();
            if (projects.stream().noneMatch(wp -> wp.projectId().equals(projectId))) {
                project.delete();
                LOG.info("ProjectsSynchronizer Job removed project: project_id={} project_name={}",
                        projectId, project.info().name());
            }
        }
    }

    @Override
    public SynchronizationMode synchronizationMode() {
        return SynchronizationMode.FULL_SYNC;
    }
}
