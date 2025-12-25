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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.workspace.Mappers;

public class WorkspaceProjectPublicResource {

    private final ProjectManager projectManager;

    public WorkspaceProjectPublicResource(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    @GET
    public ProjectResponse infoResource() {
        ProjectManager.DetailedProjectInfo detail = projectManager.detailedInfo();
        return Mappers.toProjectResponse(detail);
    }

    @Path("/repository")
    public ProjectRepositoryPublicResource repositoryResource() {
        return new ProjectRepositoryPublicResource(projectManager);
    }
}
