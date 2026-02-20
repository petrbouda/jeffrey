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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.resources.request.ProjectSettingsUpdateRequest;
import pbouda.jeffrey.platform.resources.response.ProjectSettingsResponse;
import pbouda.jeffrey.provider.platform.repository.ProjectRepository;

public class ProjectSettingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectSettingsResource.class);

    private final ProjectRepository projectRepository;

    public ProjectSettingsResource(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @POST
    public void update(ProjectSettingsUpdateRequest settings) {
        LOG.debug("Updating project settings");
        if (settings.name() != null) {
            projectRepository.updateProjectName(settings.name());
        }
    }

    @GET
    public ProjectSettingsResponse settings() {
        LOG.debug("Fetching project settings");
        return projectRepository.find()
                .map(ProjectSettingsResponse::new)
                .orElseThrow(NotFoundException::new);
    }
}
