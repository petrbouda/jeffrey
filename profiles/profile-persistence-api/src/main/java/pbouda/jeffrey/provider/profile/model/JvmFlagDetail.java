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

package pbouda.jeffrey.provider.profile.model;

import java.util.List;

/**
 * Represents a JVM flag with full details for the Flag Dashboard.
 *
 * @param name           Flag name (e.g., "UseG1GC", "MaxHeapSize")
 * @param value          Current (latest) flag value as string
 * @param type           Flag type derived from event type (Boolean, Int, UnsignedInt, Long, String)
 * @param origin         How the flag was set (Default, Ergonomic, Command line, Management)
 * @param previousValues List of previous values if the flag changed during recording
 * @param hasChanged     Whether the flag value changed during the recording
 * @param description    Optional description of the flag from OpenJDK documentation
 */
public record JvmFlagDetail(
        String name,
        String value,
        String type,
        String origin,
        List<String> previousValues,
        boolean hasChanged,
        String description
) {
    /**
     * Creates a new JvmFlagDetail with the same values but with the given description.
     *
     * @param description the description to add
     * @return a new JvmFlagDetail with the description
     */
    public JvmFlagDetail withDescription(String description) {
        return new JvmFlagDetail(name, value, type, origin, previousValues, hasChanged, description);
    }
}
