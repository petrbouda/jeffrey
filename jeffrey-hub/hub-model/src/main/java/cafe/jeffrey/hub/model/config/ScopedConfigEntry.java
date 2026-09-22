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


package cafe.jeffrey.hub.model.config;

import cafe.jeffrey.shared.common.config.ConfigType;

import java.time.Instant;

/**
 * One stored configuration value. A scope holds at most one entry per type.
 *
 * <p>The value is kept verbatim as the operator wrote it, placeholders and all: the hub validates
 * it but never interprets it, and the provisioner is what expands it against a session's layout.</p>
 */
public record ScopedConfigEntry(ScopedConfigKey key, ConfigType type, String value, Instant updatedAt) {

    public ScopedConfigEntry {
        if (key == null || type == null || updatedAt == null) {
            throw new IllegalArgumentException("key, type and updatedAt must not be null");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
