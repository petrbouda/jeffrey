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

package pbouda.jeffrey.local.core.resources.workspace;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.resources.project.ProjectResource;
import pbouda.jeffrey.local.core.resources.response.ProfileInfoResponse;
import pbouda.jeffrey.local.core.resources.response.ProjectResponse;
import pbouda.jeffrey.local.core.resources.response.ProjectWithProfilesResponse;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceProjectsResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectsResource.class);

    private final RemoteWorkspaceInfo workspaceInfo;
    private final ProjectsManager projectsManager;
    private final ProfileResourceFactory profileResourceFactory;
    private final Clock clock;

    public WorkspaceProjectsResource(
            RemoteWorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            ProfileResourceFactory profileResourceFactory,
            Clock clock) {
        this.workspaceInfo = workspaceInfo;
        this.projectsManager = workspaceManager.projectsManager();
        this.profileResourceFactory = profileResourceFactory;
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
    public List<ProjectResponse> projects(
            @QueryParam("includeDeleted") @DefaultValue("false") boolean includeDeleted) {
        var managers = includeDeleted
                ? projectsManager.findAllIncludingDeleted()
                : projectsManager.findAll();
        var result = managers.stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
        LOG.debug("Listed projects: workspaceId={} includeDeleted={} count={}", workspaceInfo.id(), includeDeleted, result.size());
        return result;
    }

    @GET
    @Path("/namespaces")
    public List<String> namespaces() {
        var result = projectsManager.findAllNamespaces();
        LOG.debug("Listed namespaces: workspaceId={} count={}", workspaceInfo.id(), result.size());
        return result;
    }
}
