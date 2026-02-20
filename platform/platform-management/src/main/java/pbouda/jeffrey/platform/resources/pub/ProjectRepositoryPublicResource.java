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
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectRepositoryPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRepositoryPublicResource.class);

    private final RepositoryManager repositoryManager;

    public ProjectRepositoryPublicResource(ProjectManager projectManager) {
        this.repositoryManager = projectManager.repositoryManager();
    }

    @Path("/sessions")
    public ProjectRepositorySessionPublicResource repositorySessions() {
        return new ProjectRepositorySessionPublicResource(repositoryManager);
    }

    @GET
    @Path("/statistics")
    public RepositoryStatisticsResponse getRepositoryStatistics() {
        LOG.debug("Fetching public repository statistics");
        RepositoryStatistics stats = repositoryManager.calculateRepositoryStatistics();
        return RepositoryStatisticsResponse.from(stats);
    }
}
