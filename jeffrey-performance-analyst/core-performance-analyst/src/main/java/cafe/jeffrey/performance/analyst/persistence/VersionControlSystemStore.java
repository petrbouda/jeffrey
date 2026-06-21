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

package cafe.jeffrey.performance.analyst.persistence;

import java.util.Optional;

/**
 * Persistence for a project's {@link VersionControlSystem} integration (one row per project). Credentials are
 * encrypted at rest and decrypted on read inside the implementation.
 */
public interface VersionControlSystemStore {

    Optional<VersionControlSystem> findByProject(String projectId);

    void upsert(VersionControlSystem versionControlSystem);

    void delete(String projectId);
}
