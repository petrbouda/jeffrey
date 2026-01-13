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
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
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
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    /**
     * @param projectManager           Primary Project Manager
     * @param projectsManager          Projects Manager to retrieve Profiles from different Projects
     * @param oqlAssistantService      AI-powered OQL assistant service
     * @param heapDumpContextExtractor Extracts heap dump context for AI prompts
     */
    public ProjectResource(
            ProjectManager projectManager,
            ProjectsManager projectsManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.projectManager = projectManager;
        this.projectsManager = projectsManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/profiles")
    public ProjectProfilesResource profilesResource() {
        return new ProjectProfilesResource(
                projectManager.profilesManager(),
                projectsManager,
                oqlAssistantService,
                heapDumpContextExtractor);
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

    @Path("/download")
    public ProjectDownloadTaskResource downloadTaskResource() {
        return new ProjectDownloadTaskResource(
                projectManager.info().workspaceId(),
                projectManager.info().id(),
                projectManager.recordingsDownloadManager());
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
