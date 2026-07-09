/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.util.Optional;

public class DeleteProjectWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteProjectWorkspaceEventConsumer.class);

    private final HubPlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;

    public DeleteProjectWorkspaceEventConsumer(
            HubPlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory) {

        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsManager projectsManager) {
        Optional<ProjectManager> project = projectsManager.project(event.projectId());
        if (project.isEmpty()) {
            LOG.error("Project not found for deleting: project_id={}", event.projectId());
            return;
        }
        ProjectManager projectManager = project.get();

        // 1. SQL cascade deletes all project metadata (instances, sessions, schedulers, etc.).
        //    Runs first: the event executes inside a database transaction, and the remote-storage
        //    cleanup below is an irreversible side effect that must not precede a possible rollback
        platformRepositories.newProjectRepository(projectManager.info().id())
                .delete();

        // 2. Delete remote repository storage sessions (best-effort: a failure here must not
        //    fail the event — orphaned files are cheaper than a permanently stuck project delete)
        try {
            RepositoryStorage remoteStorage = remoteRepositoryStorageFactory.apply(projectManager.info());
            remoteStorage.listSessions(false).forEach(session -> {
                try {
                    remoteStorage.deleteSession(session.id());
                } catch (Exception e) {
                    LOG.warn("Failed to delete remote session: sessionId={}", session.id(), e);
                }
            });
        } catch (Exception e) {
            LOG.warn("Failed to clean up remote storage for project: projectId={}", event.projectId(), e);
        }

        LOG.debug("Deleted project from workspace event: project_id={}", event.projectId());
        JfrMessageEmitter.projectDeleted(event.projectId());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_DELETED;
    }
}
