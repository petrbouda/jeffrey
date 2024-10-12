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

package pbouda.jeffrey.controller.project;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.ProjectManager;

public class ProjectResource {

    private final ProjectManager projectManager;

    public ProjectResource(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    @Path("/profiles")
    public ProjectProfilesResource profilesResource() {
        return new ProjectProfilesResource(projectManager.profilesManager());
    }

    @Path("/settings")
    public ProjectSettingsResource settingsResource() {
        return new ProjectSettingsResource(projectManager);
    }

    @Path("/recordings")
    public ProjectRecordingsResource recordingResource() {
        return new ProjectRecordingsResource(projectManager);
    }

    @DELETE
//    public List<ProjectInfo> deleteProfile(@PathParam("projectId") String projectId) {
    public void delete() {
        projectManager.cleanup();
    }
}
