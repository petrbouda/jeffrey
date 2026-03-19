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

package pbouda.jeffrey.platform.resources.workspace;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.configuration.ProjectParamsResolver;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.resources.project.ProjectResource;
import pbouda.jeffrey.platform.resources.request.CreateProjectRequest;
import pbouda.jeffrey.platform.resources.response.ProfileInfoResponse;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.response.ProjectWithProfilesResponse;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkspaceProjectsResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectsResource.class);

    private final WorkspaceInfo workspaceInfo;
    private final ProjectsManager projectsManager;
    private final ProfileResourceFactory profileResourceFactory;
    private final ProjectParamsResolver projectParamsResolver;
    private final Clock clock;

    public WorkspaceProjectsResource(
            WorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            ProfileResourceFactory profileResourceFactory,
            ProjectParamsResolver projectParamsResolver,
            Clock clock) {
        this.workspaceInfo = workspaceInfo;
        this.projectsManager = workspaceManager.projectsManager();
        this.profileResourceFactory = profileResourceFactory;
        this.projectParamsResolver = projectParamsResolver;
        this.clock = clock;
    }

    @Path("/{projectId}")
    public ProjectResource projectResource(@PathParam("projectId") String projectId) {
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        return new ProjectResource(
                projectManager,
                projectsManager,
                profileResourceFactory,
                projectParamsResolver,
                clock);
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
        LOG.debug("Listed projects with profiles: workspaceId={} projectCount={}", workspaceInfo.id(), responses.size());
        return responses;
    }

    @GET
    public List<ProjectResponse> projects() {
        var result = projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(d -> Mappers.toProjectResponse(d, projectParamsResolver.isCollectorOnlyModeEnabled(d.projectInfo())))
                .toList();
        LOG.debug("Listed projects: workspaceId={} count={}", workspaceInfo.id(), result.size());
        return result;
    }

    @GET
    @Path("/namespaces")
    public List<String> namespaces() {
        var result = projectsManager.findAllNamespaces();
        LOG.debug("Listed namespaces: workspaceId={} count={}", workspaceInfo.id(), result.size());
        return result;
    }

    @POST
    public Response createProject(CreateProjectRequest request) {
        LOG.debug("Creating project: workspaceId={} name={} templateId={}", workspaceInfo.id(), request.name(), request.templateId());
        if (this.workspaceInfo.isLive()) {
            throw new BadRequestException("Only Live workspace can be used to create a project");
        }

        CreateProject createProject = new CreateProject(
                request.originProjectId(),
                request.name(),
                request.label(),
                null, // namespace - can be set later via update
                request.templateId(),
                null, // No origin created at for API created projects
                Map.of());

        ProjectManager projectManager = projectsManager.create(createProject);
        if (projectManager != null) {
            var detailedInfo = projectManager.detailedInfo();
            boolean collectorOnly = projectParamsResolver.isCollectorOnlyModeEnabled(detailedInfo.projectInfo());
            ProjectResponse entity = Mappers.toProjectResponse(detailedInfo, collectorOnly);
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
