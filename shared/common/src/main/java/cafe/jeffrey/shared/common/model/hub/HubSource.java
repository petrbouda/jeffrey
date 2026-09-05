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

package cafe.jeffrey.shared.common.model.hub;

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
