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
import java.util.Optional;

/**
 * CRUD over the installation-wide list of {@link AdvisorProjectFolder} entries in the Microscope core
 * database.
 */
public interface AdvisorProjectFolderRepository {

    /** All folders, ordered by name so the list and the Advisor picker read the same way. */
    List<AdvisorProjectFolder> findAll();

    Optional<AdvisorProjectFolder> find(String folderId);

    /** Looks a folder up by its user-chosen name, which is unique across the installation. */
    Optional<AdvisorProjectFolder> findByName(String name);

    void insert(AdvisorProjectFolder folder);

    void update(AdvisorProjectFolder folder);

    void delete(String folderId);
}
