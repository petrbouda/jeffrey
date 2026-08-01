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
 * A profile's stored Advisor configuration: the working-copy location on this machine. Other advisor
 * knobs (such as the prune threshold) are installation-wide and live in the global settings, not here.
 *
 * @param profileId  the profile the settings belong to
 * @param sourcePath absolute path to the working copy on this machine, or null
 * @param modifiedAt when the row was last written
 */
public record AdvisorSettingsRow(
        String profileId,
        String sourcePath,
        Instant modifiedAt) {
}
