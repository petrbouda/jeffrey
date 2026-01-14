/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.provider.profile.model.JvmFlagDetail;

import java.util.List;
import java.util.Map;

/**
 * Response data for the JVM Flags dashboard.
 *
 * @param flagsByOrigin Map of flags grouped by origin (Default, Ergonomic, Command line, Management)
 * @param totalFlags    Total number of flags
 * @param changedFlags  Number of flags whose values changed during the recording
 */
public record FlagsData(
        Map<String, List<JvmFlagDetail>> flagsByOrigin,
        int totalFlags,
        int changedFlags
) {
}
