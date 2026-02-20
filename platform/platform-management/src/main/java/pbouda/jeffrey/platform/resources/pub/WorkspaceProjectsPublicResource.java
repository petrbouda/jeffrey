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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.workspace.Mappers;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WorkspaceProjectsPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectsPublicResource.class);

    private final ProjectsManager projectsManager;
    private final ProfilerRepository profilerRepository;

    public WorkspaceProjectsPublicResource(ProjectsManager projectsManager, ProfilerRepository profilerRepository) {
        this.projectsManager = projectsManager;
        this.profilerRepository = profilerRepository;
    }

    @Path("/{projectId}")
    public WorkspaceProjectPublicResource projectResource(@PathParam("projectId") String projectId) {
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));

        return new WorkspaceProjectPublicResource(projectManager, profilerRepository);
    }

    @GET
    public List<ProjectResponse> projects() {
        LOG.debug("Listing public projects");
        return projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
    }
}
