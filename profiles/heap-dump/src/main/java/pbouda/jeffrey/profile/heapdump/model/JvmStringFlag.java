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

package pbouda.jeffrey.profile.heapdump.model;

/**
 * Represents a JVM flag related to String handling extracted from JFR events.
 *
 * @param name        Flag name (e.g., "UseStringDeduplication")
 * @param value       Flag value as string
 * @param type        Flag type (Boolean, Int, String, etc.)
 * @param origin      How the flag was set (Default, Ergonomic, Command line, Management)
 * @param description Human-readable description of the flag
 */
public record JvmStringFlag(
        String name,
        String value,
        String type,
        String origin,
        String description
) {
}
