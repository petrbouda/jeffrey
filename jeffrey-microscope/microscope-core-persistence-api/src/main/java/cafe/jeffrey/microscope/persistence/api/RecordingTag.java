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

/**
 * A key-value tag attached to a recording.
 * <p>
 * Tags whose {@code key} starts with the {@link #SYSTEM_KEY_PREFIX} are application-managed
 * (set automatically when a recording lands in Recordings from a project session) and
 * are read-only — controllers must reject mutations on them. All other keys are reserved
 * for user-defined tags.
 */
public record RecordingTag(String key, String value) {

    public static final String SYSTEM_KEY_PREFIX = "origin.";

    public RecordingTag {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Tag key cannot be null or blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("Tag value cannot be null");
        }
    }

    public boolean isSystem() {
        return key.startsWith(SYSTEM_KEY_PREFIX);
    }
}
