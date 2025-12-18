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

package pbouda.jeffrey.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;

import java.util.Optional;

public class DeleteSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteSessionWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final Repositories repositories;
    private final RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory;

    public DeleteSessionWorkspaceEventConsumer(
            ProjectsManager projectsManager,
            Repositories repositories,
            RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory) {

        this.projectsManager = projectsManager;
        this.repositories = repositories;
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
        repositories.newProjectRepositoryRepository(projectManager.info().id())
                .deleteSession(event.originEventId());

        // Delete session from remote storage (e.g., S3, filesystem)
        remoteRepositoryStorageFactory.apply(projectManager.info())
                .deleteSession(event.originEventId());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.SESSION_DELETED;
    }
}
