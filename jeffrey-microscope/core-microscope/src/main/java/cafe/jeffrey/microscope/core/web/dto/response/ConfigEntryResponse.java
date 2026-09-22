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


package cafe.jeffrey.microscope.core.web.dto.response;

import cafe.jeffrey.microscope.model.config.ConfigEntry;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;

import java.util.List;

/**
 * One configuration value, carrying the scope it belongs to.
 *
 * <p>A scope holds at most one value per type, so these calls answer with a flat list and the UI
 * groups by scope. {@code workspaceId} is null for the global scope and {@code projectId} for
 * everything but a project.</p>
 *
 * @param updatedAt epoch millis, as every timestamp this API sends
 */
public record ConfigEntryResponse(
        ConfigScope scope,
        String workspaceId,
        String projectId,
        ConfigType type,
        String value,
        long updatedAt) {

    public static ConfigEntryResponse from(ConfigEntry entry) {
        return new ConfigEntryResponse(
                entry.scope(),
                entry.workspaceId(),
                entry.projectId(),
                entry.type(),
                entry.value(),
                entry.updatedAt().toEpochMilli());
    }

    public static List<ConfigEntryResponse> from(List<ConfigEntry> entries) {
        return entries.stream().map(ConfigEntryResponse::from).toList();
    }
}
