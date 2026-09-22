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

import java.util.List;

/**
 * Everything one scope holds, together with the identity of the file it renders to.
 *
 * <p>The digest is computed from the rendered bytes rather than stored beside the entries, so it
 * cannot describe a file that was never written. It is empty when the scope holds nothing, which
 * is also when the scope has no file at all.</p>
 */
public record ScopedConfig(ScopedConfigKey key, List<ScopedConfigEntry> entries, String digest) {

    public ScopedConfig {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
        digest = digest == null ? "" : digest;
    }

    public static ScopedConfig empty(ScopedConfigKey key) {
        return new ScopedConfig(key, List.of(), "");
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
