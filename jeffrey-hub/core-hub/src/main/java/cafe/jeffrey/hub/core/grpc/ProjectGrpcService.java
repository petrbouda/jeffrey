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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;

import java.util.List;

public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectGrpcService.class);

    private final GrpcLookups lookups;
    private final StorageOverviewCache storageOverviewCache;

    public ProjectGrpcService(GrpcLookups lookups, StorageOverviewCache storageOverviewCache) {
        this.lookups = lookups;
        this.storageOverviewCache = storageOverviewCache;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            List<ProjectInfo> projects = lookups.workspace(request.getWorkspaceId()).projectsManager().findAll().stream()
                    .map(this::project)
                    .toList();

            LOG.debug("Listed projects via gRPC: workspace_id={} count={}", request.getWorkspaceId(), projects.size());

            return ListProjectsResponse.newBuilder()
                    .addAllProjects(projects)
                    .build();
        });
    }

    @Override
    public void getProject(GetProjectRequest request, StreamObserver<GetProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.project(request.getWorkspaceId(), request.getProjectId());

            LOG.debug("Fetched project via gRPC: workspace_id={} project_id={}",
                    request.getWorkspaceId(), request.getProjectId());

            return GetProjectResponse.newBuilder()
                    .setProject(project(project))
                    .build();
        });
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<DeleteProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            lookups.project(request.getWorkspaceId(), request.getProjectId()).delete();

            LOG.debug("Deleted project via gRPC: workspace_id={} project_id={}",
                    request.getWorkspaceId(), request.getProjectId());

            return DeleteProjectResponse.getDefaultInstance();
        });
    }

    /**
     * The project with the size the last retention pass measured — read from that pass's
     * figures, never by walking the project's files on request.
     */
    private ProjectInfo project(ProjectManager project) {
        ProjectManager.DetailedProjectInfo detail = project.detailedInfo();
        return ProtoMappers.project(detail,
                storageOverviewCache.projectSize(detail.projectInfo().workspaceId(), detail.projectInfo().id()));
    }
}
