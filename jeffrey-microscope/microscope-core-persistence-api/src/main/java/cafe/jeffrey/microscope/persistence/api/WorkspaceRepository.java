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

package cafe.jeffrey.microscope.persistence.api;

import java.util.List;

/**
 * Local-only repository for managing a single remote workspace stored in the local database.
 */
public interface WorkspaceRepository {

    /**
     * Delete the workspace and all its local data (profiles, recordings, profiler settings).
     *
     * @return list of profile IDs that were deleted (for filesystem cleanup of per-profile databases)
     */
    List<String> delete();
}
