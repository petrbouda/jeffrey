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

import java.time.Instant;

/**
 * A named working copy on this machine the Advisor may read, managed from Settings → Advisor.
 *
 * <p>Deliberately unconnected to workspaces, projects or hubs: an entry is nothing but a name the user
 * chose and an absolute path. The name is what identifies the folder when it is picked for an Advisor
 * run, so it carries the meaning here — the path only says where to read.
 *
 * @param folderId  generated identifier of the entry
 * @param name      the user-chosen name, unique across the installation
 * @param path      absolute path to the working copy on this machine
 * @param createdAt when the entry was created
 */
public record AdvisorProjectFolder(
        String folderId,
        String name,
        String path,
        Instant createdAt) {
}
