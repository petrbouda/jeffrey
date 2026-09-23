/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

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
