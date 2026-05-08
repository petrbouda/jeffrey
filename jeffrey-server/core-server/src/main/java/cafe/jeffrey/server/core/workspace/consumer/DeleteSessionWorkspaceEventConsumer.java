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

package cafe.jeffrey.server.core.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.server.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.server.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class DeleteSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteSessionWorkspaceEventConsumer.class);

    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryStorage.Factory remoteRepositoryStorageFactory;
    private final Clock clock;

    public DeleteSessionWorkspaceEventConsumer(
            ServerPlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory,
            Clock clock) {

        this.platformRepositories = platformRepositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
        this.clock = clock;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsManager projectsManager) {
        Optional<ProjectManager> project = projectsManager.project(event.projectId());
        if (project.isEmpty()) {
            LOG.error("Project not found for deleting session: project_id: {}", event.projectId());
            return;
        }
        ProjectManager projectManager = project.get();
        String projectId = projectManager.info().id();

        // Look up session before deletion to get instanceId
        ProjectRepositoryRepository repoRepo = platformRepositories.newProjectRepositoryRepository(projectId);
        Optional<ProjectInstanceSessionInfo> sessionOpt = repoRepo.findSessionById(event.originEventId());
        String instanceId = sessionOpt.map(ProjectInstanceSessionInfo::instanceId).orElse(null);

        // Delete session from project repository (from Database)
        repoRepo.deleteSession(event.originEventId());

        // Delete session from remote storage (e.g., S3, filesystem)
        remoteRepositoryStorageFactory.apply(projectManager.info())
                .deleteSession(event.originEventId());

        LOG.debug("Deleted session from workspace event: project_id={} session_id={}", event.projectId(), event.originEventId());
        JfrMessageEmitter.sessionDeleted(event.originEventId(), projectId);

        // Update instance expiring/expired status
        if (instanceId != null) {
            ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);
            Optional<ProjectInstanceInfo> instanceOpt = instanceRepo.find(instanceId);
            if (instanceOpt.isPresent()) {
                ProjectInstanceInfo instance = instanceOpt.get();
                Instant now = clock.instant();

                // Set expiring_at on first session deletion
                if (instance.expiringAt() == null) {
                    instanceRepo.setExpiringAt(instanceId, now);
                }

                // If no remaining sessions and instance is FINISHED → EXPIRED
                List<ProjectInstanceSessionInfo> remainingSessions = instanceRepo.findSessions(instanceId);
                if (remainingSessions.isEmpty() && instance.status() == ProjectInstanceStatus.FINISHED) {
                    instanceRepo.updateStatusAndExpiredAt(instanceId, ProjectInstanceStatus.EXPIRED, now);
                    LOG.info("Instance marked as EXPIRED (last session deleted): instanceId={} projectId={}",
                            instanceId, projectId);
                }
            }
        }
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED;
    }
}
