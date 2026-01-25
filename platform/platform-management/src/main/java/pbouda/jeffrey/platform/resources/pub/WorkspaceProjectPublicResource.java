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
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

public class WorkspaceProjectPublicResource {

    private final ProjectManager projectManager;
    private final ProfilerRepository profilerRepository;

    public WorkspaceProjectPublicResource(ProjectManager projectManager, ProfilerRepository profilerRepository) {
        this.projectManager = projectManager;
        this.profilerRepository = profilerRepository;
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

    @Path("/profiler/settings")
    public ProjectProfilerSettingsPublicResource profilerSettingsResource() {
        ProjectInfo projectInfo = projectManager.info();
        return new ProjectProfilerSettingsPublicResource(profilerRepository, projectInfo.workspaceId(), projectInfo.id());
    }

    @Path("/messages")
    public ProjectMessagesPublicResource messagesResource() {
        return new ProjectMessagesPublicResource(projectManager.messagesManager());
    }

    @Path("/instances")
    public ProjectInstancesPublicResource instancesResource() {
        return new ProjectInstancesPublicResource(projectManager.projectInstanceRepository());
    }
}
