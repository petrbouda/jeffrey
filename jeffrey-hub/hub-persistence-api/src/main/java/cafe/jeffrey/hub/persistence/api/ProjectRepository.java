/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.hub.model.ProjectInfo;

import java.util.Optional;

public interface ProjectRepository {

    /**
     * Delete the project and all its related data.
     */
    void delete();

    /**
     * Find the project information.
     *
     * @return project information.
     */
    Optional<ProjectInfo> find();

    /**
     * Find the project information regardless of its soft-deleted state. Needed by
     * operations that must resolve soft-deleted projects, e.g. restoring them.
     *
     * @return project information, including soft-deleted projects.
     */
    Optional<ProjectInfo> findIncludingDeleted();


    /**
     * Restore a previously soft-deleted project, clearing the deleted_at timestamp.
     */
    void restore();

}
