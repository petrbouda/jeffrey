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

package pbouda.jeffrey.local.core.resources.response;

import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

public record ProjectSettingsResponse(
        String id,
        String name,
        String description,
        String createdAt,
        boolean blocked,
        Boolean streamingEnabled,
        boolean effectiveStreamingEnabled,
        String effectiveStreamingLevel) {

    /**
     * Resolves the effective streaming state from project > workspace > global hierarchy.
     */
    public static ProjectSettingsResponse create(ProjectInfo projectInfo, Boolean workspaceStreamingEnabled) {
        Boolean projectStreaming = projectInfo.streamingEnabled();

        boolean effectiveEnabled;
        String level;

        if (projectStreaming != null) {
            effectiveEnabled = projectStreaming;
            level = "PROJECT";
        } else if (workspaceStreamingEnabled != null) {
            effectiveEnabled = workspaceStreamingEnabled;
            level = "WORKSPACE";
        } else {
            effectiveEnabled = true;
            level = "GLOBAL";
        }

        return new ProjectSettingsResponse(
                projectInfo.id(),
                projectInfo.name(),
                null,
                InstantUtils.formatInstant(projectInfo.createdAt()),
                projectInfo.blocked(),
                projectStreaming,
                effectiveEnabled,
                level);
    }
}
