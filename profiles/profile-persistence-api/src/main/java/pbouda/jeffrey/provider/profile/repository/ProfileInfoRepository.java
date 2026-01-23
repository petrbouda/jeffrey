/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile.repository;

import java.util.Optional;

/**
 * Repository for accessing profile context information (workspace_id, project_id).
 * The profile_info table stores a single row per profile database with context
 * needed for navigation and API access.
 */
public interface ProfileInfoRepository {

    /**
     * Information about the profile's location in the workspace/project hierarchy.
     *
     * @param profileId   the unique identifier of the profile
     * @param projectId   the project this profile belongs to
     * @param workspaceId the workspace this profile belongs to
     */
    record ProfileContext(String profileId, String projectId, String workspaceId) {
    }

    /**
     * Inserts the profile context information.
     * Called once when the profile database is created.
     *
     * @param context the profile context to insert
     */
    void insert(ProfileContext context);

    /**
     * Retrieves the profile context information.
     *
     * @return the profile context, or empty if not found
     */
    Optional<ProfileContext> find();
}
