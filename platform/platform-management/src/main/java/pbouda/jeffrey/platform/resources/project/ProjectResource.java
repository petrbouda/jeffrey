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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.resources.SchedulerResource;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.workspace.Mappers;

public class ProjectResource {

    private final ProjectManager projectManager;
    private final ProjectsManager projectsManager;

    /**
     * @param projectManager  Primary Project Manager
     * @param projectsManager Projects Manager to retrieve Profiles from different Projects
     */
    public ProjectResource(ProjectManager projectManager, ProjectsManager projectsManager) {
        this.projectManager = projectManager;
        this.projectsManager = projectsManager;
    }

    @Path("/profiles")
    public ProjectProfilesResource profilesResource() {
        return new ProjectProfilesResource(projectManager.profilesManager(), projectsManager);
    }

    @Path("/settings")
    public ProjectSettingsResource settingsResource() {
        return new ProjectSettingsResource(projectManager.projectRepository());
    }

    @Path("/profiler/settings")
    public ProjectProfilerSettingsResource profilerSettingsResource() {
        return new ProjectProfilerSettingsResource(projectManager.profilerSettingsManager());
    }

    @Path("/recordings")
    public ProjectRecordingsResource recordingResource() {
        return new ProjectRecordingsResource(projectManager.recordingsManager());
    }

    @Path("/repository")
    public ProjectRepositoryResource repositoryResource() {
        return new ProjectRepositoryResource(projectManager);
    }

    @Path("/scheduler")
    public SchedulerResource schedulerResource() {
        return new SchedulerResource(projectManager.schedulerManager());
    }

    @Path("/messages")
    public ProjectMessagesResource messagesResource() {
        return new ProjectMessagesResource(projectManager.messagesManager());
    }

    @GET
    @Path("/initializing")
    public boolean initializing() {
        return projectManager.isInitializing();
    }

    @GET
    public ProjectResponse infoResource() {
        DetailedProjectInfo detail = projectManager.detailedInfo();
        return Mappers.toProjectResponse(detail);
    }

    @DELETE
    public void delete() {
        projectManager.delete(WorkspaceEventCreator.MANUAL);
    }
}
