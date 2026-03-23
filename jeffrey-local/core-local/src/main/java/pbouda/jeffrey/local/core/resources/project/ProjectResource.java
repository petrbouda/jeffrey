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

package pbouda.jeffrey.local.core.resources.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.resources.response.ProjectResponse;
import pbouda.jeffrey.local.core.resources.workspace.Mappers;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;

public class ProjectResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectResource.class);

    private final ProjectManager projectManager;
    private final ProjectsManager projectsManager;
    private final ProfileResourceFactory profileResourceFactory;
    private final Clock clock;

    public ProjectResource(
            ProjectManager projectManager,
            ProjectsManager projectsManager,
            ProfileResourceFactory profileResourceFactory,
            Clock clock) {
        this.projectManager = projectManager;
        this.projectsManager = projectsManager;
        this.profileResourceFactory = profileResourceFactory;
        this.clock = clock;
    }

    @Path("/settings")
    public ProjectSettingsResource settingsResource() {
        return new ProjectSettingsResource(
                projectManager.info(),
                projectManager::updateName,
                projectManager::block,
                projectManager::unblock);
    }

    @Path("/profiles")
    public ProjectProfilesResource profilesResource() {
        return new ProjectProfilesResource(
                projectManager.info(),
                projectManager.profilesManager(),
                projectsManager,
                profileResourceFactory);
    }

    @Path("/recordings")
    public ProjectRecordingsResource recordingsResource() {
        return new ProjectRecordingsResource(
                projectManager.info(),
                projectManager.recordingsManager(),
                projectManager.profilesManager());
    }

    @Path("/profiler/settings")
    public ProjectProfilerSettingsResource profilerSettingsResource() {
        return new ProjectProfilerSettingsResource(projectManager.profilerSettingsManager());
    }

    @Path("/repository")
    public ProjectRepositoryResource repositoryResource() {
        return new ProjectRepositoryResource(projectManager, clock);
    }

    @Path("/download")
    public ProjectDownloadTaskResource downloadTaskResource() {
        return new ProjectDownloadTaskResource(
                projectManager.info().workspaceId(),
                projectManager.info().id(),
                projectManager.recordingsDownloadManager());
    }

    @Path("/messages")
    public ProjectMessagesResource messagesResource() {
        return new ProjectMessagesResource(projectManager.messagesManager());
    }

    @Path("/instances")
    public ProjectInstancesResource instancesResource() {
        return new ProjectInstancesResource(projectManager.info(), projectManager.instancesManager(), clock);
    }

    @GET
    @Path("/initializing")
    public boolean initializing() {
        return false;
    }

    @GET
    public ProjectResponse infoResource() {
        LOG.debug("Fetching project info: projectId={}", projectManager.info().id());
        DetailedProjectInfo detail = projectManager.detailedInfo();
        return Mappers.toProjectResponse(detail, false);
    }

    @DELETE
    public void delete() {
        LOG.debug("Deleting project: projectId={}", projectManager.info().id());
        projectManager.delete(WorkspaceEventCreator.MANUAL);
    }
}
