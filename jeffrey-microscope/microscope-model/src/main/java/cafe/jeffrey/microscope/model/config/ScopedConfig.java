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


package cafe.jeffrey.microscope.model.config;

import cafe.jeffrey.shared.common.config.ConfigScope;

import java.util.List;

/**
 * Configuration the hub holds for one scope, as Microscope knows it.
 *
 * <p>Microscope's own record rather than the hub's: the two sides share no domain type, and this
 * one is mapped from the wire at the client boundary. The scope and type enums are the shared
 * contract, which both sides and the provisioner spell the same way.</p>
 *
 * @param digest identity of the file this scope renders to, empty when it holds nothing. Comparing
 *               it with what a session recorded is what says whether a running JVM is current.
 */
public record ScopedConfig(
        ConfigScope scope,
        String workspaceId,
        String projectId,
        List<ConfigEntry> entries,
        String digest) {

    public ScopedConfig {
        entries = entries == null ? List.of() : List.copyOf(entries);
        digest = digest == null ? "" : digest;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
