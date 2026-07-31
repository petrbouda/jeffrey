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

import java.util.Optional;

/**
 * Per-project Advisor configuration in the Microscope core database. Keyed by
 * {@code (workspaceId, projectId)} because projects are listed live from the hub and have no local row
 * to hang a foreign key on.
 */
public interface AdvisorSettingsRepository {

    Optional<AdvisorSettingsRow> find(String workspaceId, String projectId);

    void upsert(AdvisorSettingsRow settings);
}
