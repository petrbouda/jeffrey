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
import cafe.jeffrey.shared.common.config.ConfigType;

import java.time.Instant;

/**
 * One configuration value the hub holds, as Microscope knows it.
 *
 * <p>The scope it belongs to travels with it: a scope holds at most one value per type, so a flat
 * list of entries is the whole of what the hub returns, and a caller that wants them grouped
 * groups by scope. {@code workspaceId} is empty for the global scope and {@code projectId} for
 * everything but a project.</p>
 */
public record ConfigEntry(
        ConfigScope scope,
        String workspaceId,
        String projectId,
        ConfigType type,
        String value,
        Instant updatedAt) {
}
