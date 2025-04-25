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

package pbouda.jeffrey.resources.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.resources.SchedulerResource;
import pbouda.jeffrey.resources.project.profile.ProfileDiffResource;
import pbouda.jeffrey.resources.project.profile.ProfileResource;

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
        return new ProjectSettingsResource(projectManager.settingsManager());
    }

    @Path("/recordings")
    public ProjectRecordingsResource recordingResource() {
        return new ProjectRecordingsResource(projectManager.recordingsManager());
    }

    @Path("/repository")
    public ProjectRepositoryResource repositoryResource() {
        return new ProjectRepositoryResource(projectManager.repositoryManager());
    }

    @Path("/scheduler")
    public SchedulerResource schedulerResource() {
        return new SchedulerResource(projectManager.schedulerManager());
    }

    @DELETE
    public void delete() {
        projectManager.delete();
    }
}
