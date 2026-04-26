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

package cafe.jeffrey.server.persistence.model;

import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;

/**
 * A session paired with its parent repository and project, returned by lookups that need to
 * resolve a session by id alone (without going through the project context).
 */
public record SessionWithRepository(
        String projectId,
        RepositoryInfo repositoryInfo,
        ProjectInstanceSessionInfo sessionInfo) {

    /**
     * Creates a minimal {@link ProjectInfo} containing only the project ID.
     * Useful for factory lookups that only need the ID to resolve project-scoped resources.
     */
    public ProjectInfo projectInfo() {
        return new ProjectInfo(projectId, null, null, null, null, null, null, null, null, null);
    }
}
