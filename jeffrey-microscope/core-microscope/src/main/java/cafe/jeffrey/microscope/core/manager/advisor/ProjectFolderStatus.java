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

package cafe.jeffrey.microscope.core.manager.advisor;

import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;

/**
 * A stored folder together with what the filesystem says about it right now. The stored row keeps a
 * path even when it stops resolving — a checkout that moved is worth showing as broken rather than
 * dropping — so presence is reported alongside it instead of being written into the row.
 *
 * @param folder  the stored entry
 * @param present whether {@link AdvisorProjectFolder#path()} is a folder on this machine
 */
public record ProjectFolderStatus(AdvisorProjectFolder folder, boolean present) {
}
