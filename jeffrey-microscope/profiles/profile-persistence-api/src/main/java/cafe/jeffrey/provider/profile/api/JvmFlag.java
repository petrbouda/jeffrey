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

/**
 * Represents a JVM flag extracted from JFR flag events.
 *
 * @param name   Flag name (e.g., "UseStringDeduplication")
 * @param value  Flag value as string
 * @param type   Flag type (Boolean, Int, UnsignedInt, etc.) derived from event type
 * @param origin How the flag was set (Default, Ergonomic, Command line, Management)
 */
public record JvmFlag(
        String name,
        String value,
        String type,
        String origin
) {
}
