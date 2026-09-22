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
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;

/**
 * Resolves domain managers/entities from request identifiers for the gRPC services, throwing a
 * consistent {@code NOT_FOUND} status when a target does not exist. Holds the lookups more than
 * one service needs — {@code repositoryManagerForSession} for the repository and download
 * services, the active-project lookup for the project and profiler-settings services, and the
 * instance lookups — so each is written once.
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
        // A session's project is always active: a soft delete takes the project's sessions with it
        return repositoryManagerFactory.apply(projectInfo(session.projectId()));
    }

    /**
     * Checks that a configuration scope names things that exist — the workspace, and the project as
     * one of that workspace's — so that a mistyped id does not store a value no reader will find.
     */
    public void requireExists(ScopedConfigKey key) {
        if (key.scope() == ConfigScope.GLOBAL) {
            return;
        }
        WorkspaceInfo workspace = platformRepositories.newWorkspacesRepository().find(key.workspaceId())
                .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + key.workspaceId()));
        if (key.scope() == ConfigScope.PROJECT) {
            ProjectInfo project = projectInfo(key.projectId());
            if (!workspace.id().equals(project.workspaceId())) {
                throw GrpcExceptions.notFound(
                        "Project not found in workspace: project_id=" + project.id() + " workspace_id=" + workspace.id());
            }
        }
    }

    public ProjectInstanceInfo instanceById(String instanceId) {
        return platformRepositories.findInstanceById(instanceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + instanceId));
    }
}
