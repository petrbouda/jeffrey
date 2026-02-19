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

package pbouda.jeffrey.platform.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;

import java.util.Optional;

public class DeleteSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteSessionWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final PlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;

    public DeleteSessionWorkspaceEventConsumer(
            ProjectsManager projectsManager,
            PlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory) {

        this.projectsManager = projectsManager;
        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        Optional<ProjectManager> project = projectsManager.project(event.projectId());
        if (project.isEmpty()) {
            LOG.error("Project not found for deleting session: project_id: {}", event.projectId());
            return;
        }
        ProjectManager projectManager = project.get();

        // Delete session from project repository (from Database)
        platformRepositories.newProjectRepositoryRepository(projectManager.info().id())
                .deleteSession(event.originEventId());

        // Delete session from remote storage (e.g., S3, filesystem)
        remoteRepositoryStorageFactory.apply(projectManager.info())
                .deleteSession(event.originEventId());

        LOG.debug("Deleted session from workspace event: project_id={} session_id={}", event.projectId(), event.originEventId());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED;
    }
}
