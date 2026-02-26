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
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DeleteProjectWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteProjectWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final PlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;
    private final JeffreyDirs jeffreyDirs;

    public DeleteProjectWorkspaceEventConsumer(
            ProjectsManager projectsManager,
            PlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory,
            JeffreyDirs jeffreyDirs) {

        this.projectsManager = projectsManager;
        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        Optional<ProjectManager> project = projectsManager.project(event.projectId());
        if (project.isEmpty()) {
            LOG.error("Project not found for deleting: project_id={}", event.projectId());
            return;
        }
        ProjectManager projectManager = project.get();

        // 1. Collect profile IDs for filesystem cleanup BEFORE SQL cascade deletes them
        List<String> profileIds = projectManager.profilesManager().allProfiles().stream()
                .map(p -> p.info().id())
                .toList();

        // 2. Delete remote repository storage sessions
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

        // 3. SQL cascade deletes ALL metadata (including profiles, instances, sessions, etc.)
        platformRepositories.newProjectRepository(projectManager.info().id())
                .delete();

        // 4. Best-effort filesystem cleanup for profile directories
        for (String profileId : profileIds) {
            try {
                Path profileDirectory = jeffreyDirs.profileDir(profileId);
                FileSystemUtils.removeDirectory(profileDirectory);
            } catch (Exception e) {
                LOG.warn("Failed to delete profile directory, will be cleaned by orphan job: profileId={}", profileId, e);
            }
        }

        LOG.debug("Deleted project from workspace event: project_id={}", event.projectId());
        JfrMessageEmitter.projectDeleted(event.projectId());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_DELETED;
    }
}
