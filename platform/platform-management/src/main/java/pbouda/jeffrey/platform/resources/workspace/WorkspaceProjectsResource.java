/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.resources.workspace;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.resources.project.ProjectResource;
import pbouda.jeffrey.platform.resources.request.CreateProjectRequest;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkspaceProjectsResource {

    public record ProfileInfoResponse(String id, String name, String projectId, Instant createdAt) {
    }

    public record ProjectWithProfilesResponse(String id, String name, List<ProfileInfoResponse> profiles) {
    }

    private final WorkspaceInfo workspaceInfo;
    private final ProjectsManager projectsManager;
    private final WorkspaceManager workspaceManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public WorkspaceProjectsResource(
            WorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.workspaceInfo = workspaceInfo;
        this.workspaceManager = workspaceManager;
        this.projectsManager = workspaceManager.projectsManager();
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/{projectId}")
    public ProjectResource projectResource(@PathParam("projectId") String projectId) {
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        return new ProjectResource(
                projectManager,
                projectsManager,
                oqlAssistantService,
                heapDumpContextExtractor);
    }

    /**
     * Originally for a Dialog to pick up the Secondary Profile.
     */
    @GET
    @Path("/profiles")
    public List<ProjectWithProfilesResponse> projectsWithProfiles() {
        List<ProjectWithProfilesResponse> responses = new ArrayList<>();
        for (ProjectManager projectManager : this.projectsManager.findAll()) {
            ProjectInfo projectInfo = projectManager.info();

            List<ProfileInfoResponse> profiles = projectManager.profilesManager().allProfiles().stream()
                    .map(manager -> {
                        ProfileInfo profileInfo = manager.info();
                        return new ProfileInfoResponse(
                                profileInfo.id(),
                                profileInfo.name(),
                                projectInfo.id(),
                                profileInfo.createdAt());
                    })
                    .toList();

            ProjectWithProfilesResponse response =
                    new ProjectWithProfilesResponse(projectInfo.id(), projectInfo.name(), profiles);

            responses.add(response);
        }
        return responses;
    }

    @GET
    public List<ProjectResponse> projects() {
        List<ProjectResponse> projects = projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();

        return projects;
    }

    @POST
    public Response createProject(CreateProjectRequest request) {
        if (this.workspaceInfo.isLive()) {
            throw new BadRequestException("Only Live workspace can be used to create a project");
        }

        CreateProject createProject = new CreateProject(
                request.originProjectId(),
                request.name(),
                request.label(),
                request.templateId(),
                null, // No origin project ID for API created projects
                Map.of());

        ProjectManager projectManager = projectsManager.create(createProject);
        if (projectManager != null) {
            ProjectResponse entity = Mappers.toProjectResponse(projectManager.detailedInfo());
            return Response.status(Status.CREATED)
                    .entity(entity)
                    .build();
        } else {
            return Response.status(Status.BAD_REQUEST)
                    .entity("Project could not be created")
                    .build();
        }
    }
}
