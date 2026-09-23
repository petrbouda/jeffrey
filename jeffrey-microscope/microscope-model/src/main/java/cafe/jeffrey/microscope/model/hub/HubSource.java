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

package cafe.jeffrey.microscope.model.hub;

import java.util.Locale;

/**
 * Who owns a locally stored hub pointer, and therefore who is allowed to change it.
 */
public enum HubSource {

    /**
     * Added through the UI. The user owns it; startup reconciliation never touches it.
     */
    USER,

    /**
     * Declared under {@code jeffrey.microscope.hubs.*}. Configuration owns it: it is created,
     * updated and removed to match the configuration on every startup, and the UI cannot delete it.
     */
    CONFIG;

    private static final HubSource DEFAULT_SOURCE = USER;

    /**
     * Reads the stored column value, tolerating anything unexpected. A row written before the
     * column existed carries {@code NULL}, and such a row is by definition user-added — falling
     * back rather than throwing keeps one bad value from making the whole registry unreadable.
     */
    public static HubSource fromDb(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SOURCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT_SOURCE;
        }
    }
}
