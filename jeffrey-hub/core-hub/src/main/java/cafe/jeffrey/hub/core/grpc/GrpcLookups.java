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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;

/**
 * Resolves domain managers/entities from request identifiers for the gRPC services, throwing a
 * consistent {@code NOT_FOUND} status when a target does not exist. Centralizes lookups that were
 * previously duplicated across services ({@code repositoryManagerForSession} in the repository and
 * download services, the active-project lookup in the project and profiler-settings services) and
 * the instance lookups that were repeated inline.
 */
public class GrpcLookups {

    private final HubPlatformRepositories platformRepositories;
    private final RepositoryManager.Factory repositoryManagerFactory;
    private final ProjectManager.Factory projectManagerFactory;

    public GrpcLookups(
            HubPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory,
            ProjectManager.Factory projectManagerFactory) {

        this.platformRepositories = platformRepositories;
        this.repositoryManagerFactory = repositoryManagerFactory;
        this.projectManagerFactory = projectManagerFactory;
    }

    public ProjectManager projectManager(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    /**
     * Resolves a project regardless of its soft-deleted state. Restore must see soft-deleted
     * projects — the active-only {@link #projectManager(String)} filters them out, which would
     * make restoring impossible.
     */
    public ProjectManager projectManagerIncludingDeleted(String projectId) {
        return platformRepositories.newProjectRepository(projectId).findIncludingDeleted()
                .map(projectManagerFactory)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    public ProjectInfo projectInfo(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    public RepositoryManager repositoryManagerForProject(String projectId) {
        return repositoryManagerFactory.apply(projectInfo(projectId));
    }

    public RepositoryManager repositoryManagerForSession(String sessionId) {
        SessionWithRepository session = platformRepositories.findSessionWithRepositoryById(sessionId)
                .orElseThrow(() -> GrpcExceptions.notFound("Session not found: " + sessionId));
        return repositoryManagerFactory.apply(session.projectInfo());
    }

    public ProjectInstanceInfo instanceById(String instanceId) {
        return platformRepositories.findInstanceById(instanceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + instanceId));
    }
}
