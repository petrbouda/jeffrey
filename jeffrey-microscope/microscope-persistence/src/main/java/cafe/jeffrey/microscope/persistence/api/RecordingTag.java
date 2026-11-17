/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
