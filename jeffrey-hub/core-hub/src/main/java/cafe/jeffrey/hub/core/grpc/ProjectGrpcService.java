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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
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
            WorkspaceManager workspace = workspacesManager.findById(request.getWorkspaceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + request.getWorkspaceId()));

            var projectsManager = workspace.projectsManager();
            var managers = request.getIncludeDeleted()
                    ? projectsManager.findAllIncludingDeleted()
                    : projectsManager.findAll();

            List<ProjectInfo> projects = managers.stream()
                    .map(ProjectManager::detailedInfo)
                    .map(ProtoMappers::project)
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
            workspacesManager.findById(request.getWorkspaceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + request.getWorkspaceId()));

            ProjectManager project = findProjectInWorkspace(request.getWorkspaceId(), request.getProjectId());

            LOG.debug("Fetched project via gRPC: workspace_id={} project_id={}",
                    request.getWorkspaceId(), request.getProjectId());

            return GetProjectResponse.newBuilder()
                    .setProject(ProtoMappers.project(project.detailedInfo()))
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

    @Override
    public void restoreProject(RestoreProjectRequest request, StreamObserver<RestoreProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.projectManagerIncludingDeleted(request.getProjectId());
            project.restore();

            LOG.info("Restored project via gRPC: project_id={}", request.getProjectId());

            return RestoreProjectResponse.getDefaultInstance();
        });
    }

    /**
     * Finds a single project within a workspace, soft-deleted or not — restore has to see a
     * deleted one — and refuses a project of another workspace as not found.
     */
    private ProjectManager findProjectInWorkspace(String workspaceId, String projectId) {
        ProjectManager project = lookups.projectManagerIncludingDeleted(projectId);
        if (!workspaceId.equals(project.info().workspaceId())) {
            throw GrpcExceptions.notFound("Project not found: " + projectId);
        }
        return project;
    }

}
