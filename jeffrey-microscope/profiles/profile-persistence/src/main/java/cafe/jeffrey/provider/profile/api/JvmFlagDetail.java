/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

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
 * @param changeHistory  Chronological list of value changes (latest first), only populated if hasChanged is true
 */
public record JvmFlagDetail(
        String name,
        String value,
        String type,
        String origin,
        List<String> previousValues,
        boolean hasChanged,
        String description,
        List<FlagValueChange> changeHistory
) {
    /**
     * Creates a new JvmFlagDetail with the same values but with the given description.
     *
     * @param description the description to add
     * @return a new JvmFlagDetail with the description
     */
    public JvmFlagDetail withDescription(String description) {
        return new JvmFlagDetail(name, value, type, origin, previousValues, hasChanged, description, changeHistory);
    }
}
