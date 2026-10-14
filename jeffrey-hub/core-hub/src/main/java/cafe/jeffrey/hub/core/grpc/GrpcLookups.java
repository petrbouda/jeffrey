/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.model.ProjectInfo;

/**
 * Resolves domain managers/entities from request identifiers for the gRPC services, throwing a
 * consistent {@code NOT_FOUND} status when a target does not exist. Holds the lookups more than
 * one service needs — {@code repositoryManagerForSession} for the repository and download
 * services, and the project lookups — so each is written once.
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
        // A session always has its project: deleting a project takes its sessions with it
        return repositoryManagerFactory.apply(projectInfo(session.projectId()));
    }

}
