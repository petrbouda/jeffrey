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

package pbouda.jeffrey.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.repository.model.ProjectInfo;
import pbouda.jeffrey.resources.project.ProjectResource;
import pbouda.jeffrey.resources.request.CreateProjectRequest;

import java.util.Comparator;
import java.util.List;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectsResource {

    private final ProjectsManager projectsManager;

    @Inject
    public ProjectsResource(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Path("/{projectId}")
    public ProjectResource settingsResource(@PathParam("projectId") String projectId) {
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(Exceptions.PROJECT_NOT_FOUND);

        return new ProjectResource(projectManager);
    }

    @GET
    public List<ProjectInfo> projects() {
        return allProjects();
    }

    @POST
    public Response createProfile(CreateProjectRequest request) {
        try {
            projectsManager.create(new ProjectInfo(request.name()));
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
        return Response.ok(allProjects()).build();
    }

    private List<ProjectInfo> allProjects() {
        return projectsManager.allProjectInfos().stream()
                .sorted(Comparator.comparing(ProjectInfo::createdAt))
                .toList();
    }
}
