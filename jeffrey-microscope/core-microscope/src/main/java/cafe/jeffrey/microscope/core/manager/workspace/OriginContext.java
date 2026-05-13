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

package cafe.jeffrey.microscope.core.manager.workspace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures the (server, workspace, project) that a recording was downloaded from.
 * Used to materialise {@code origin.*} system tags on the freshly imported QA recording.
 */
public record OriginContext(
        String serverId,
        String serverName,
        String workspaceId,
        String workspaceRef,
        String projectId,
        String projectName) {

    /**
     * Builds the {@code origin.*} system tag map for a recording downloaded from an upstream session.
     *
     * @param upstreamRecordingId the upstream session/recording id (used for dedup of repeat downloads)
     */
    public Map<String, String> toTagMap(String upstreamRecordingId) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (serverId != null) {
            tags.put("origin.serverId", serverId);
        }
        if (serverName != null) {
            tags.put("origin.server", serverName);
        }
        if (workspaceId != null) {
            tags.put("origin.workspaceId", workspaceId);
        }
        if (workspaceRef != null) {
            tags.put("origin.workspace", workspaceRef);
        }
        if (projectId != null) {
            tags.put("origin.projectId", projectId);
        }
        if (projectName != null) {
            tags.put("origin.project", projectName);
        }
        if (upstreamRecordingId != null) {
            tags.put("origin.recordingId", upstreamRecordingId);
        }
        return tags;
    }
}
