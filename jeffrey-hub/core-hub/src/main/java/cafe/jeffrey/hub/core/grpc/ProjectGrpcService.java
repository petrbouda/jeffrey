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
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

import java.util.List;

public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectGrpcService.class);

    private final WorkspacesManager workspacesManager;
    private final GrpcLookups lookups;

    public ProjectGrpcService(WorkspacesManager workspacesManager, GrpcLookups lookups) {
        this.workspacesManager = workspacesManager;
        this.lookups = lookups;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());
            String referenceId = workspace.localInfo().referenceId();
            List<ProjectInfo> projects = workspace.projectsManager().findAll().stream()
                    .map(project -> ProtoMappers.project(project.detailedInfo(), referenceId))
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
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());
            ProjectManager project = findProjectInWorkspace(request.getWorkspaceId(), request.getProjectId());

            LOG.debug("Fetched project via gRPC: workspace_id={} project_id={}",
                    request.getWorkspaceId(), request.getProjectId());

            return GetProjectResponse.newBuilder()
                    .setProject(ProtoMappers.project(project.detailedInfo(), workspace.localInfo().referenceId()))
                    .build();
        });
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<DeleteProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.projectManager(request.getProjectId());
            project.delete();

            LOG.debug("Deleted project via gRPC: project_id={}", request.getProjectId());

            return DeleteProjectResponse.getDefaultInstance();
        });
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + workspaceId));
    }

    /**
     * Finds a single project within a workspace and refuses a project of another workspace as
     * not found.
     */
    private ProjectManager findProjectInWorkspace(String workspaceId, String projectId) {
        ProjectManager project = lookups.projectManager(projectId);
        if (!workspaceId.equals(project.info().workspaceId())) {
            throw GrpcExceptions.notFound("Project not found: " + projectId);
        }
        return project;
    }
}
