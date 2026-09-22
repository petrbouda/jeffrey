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

import cafe.jeffrey.microscope.model.config.ScopedConfig;
import cafe.jeffrey.shared.common.config.ConfigScope;

import java.util.List;

/**
 * What the hub holds for one scope.
 *
 * @param digest identity of the file this scope renders to, empty when it holds nothing. The UI
 *               compares it with what a session recorded to say whether a running JVM is current.
 */
public record ScopedConfigResponse(
        ConfigScope scope,
        String workspaceId,
        String projectId,
        List<ConfigEntryResponse> entries,
        String digest) {

    public static ScopedConfigResponse from(ScopedConfig config) {
        return new ScopedConfigResponse(
                config.scope(),
                config.workspaceId(),
                config.projectId(),
                config.entries().stream().map(ConfigEntryResponse::from).toList(),
                config.digest());
    }
}
