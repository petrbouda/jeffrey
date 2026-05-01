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
