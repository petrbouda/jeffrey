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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.Locale;

/**
 * What a scan should keep, in the two halves the two sides can each answer.
 * <p>
 * The name filters are matched here, because a hub knows nothing about how a reader refers to it.
 * The {@link RecordingSessionFilter} is pushed down to each hub instead, so a window or a status
 * narrows the listing before it crosses the network rather than after.
 *
 * @param hub       a hub id, or a fragment of a hub name; {@code null} for every hub
 * @param workspace a fragment of a workspace name or reference id; {@code null} for every workspace
 * @param project   a fragment of a project name or label; {@code null} for every project
 * @param sessions  what each hub is asked for
 */
public record HubScanFilter(
        String hub,
        String workspace,
        String project,
        RecordingSessionFilter sessions) {

    public static final HubScanFilter ALL = new HubScanFilter(null, null, null, RecordingSessionFilter.ALL);

    public HubScanFilter {
        hub = normalise(hub);
        workspace = normalise(workspace);
        project = normalise(project);
        sessions = sessions == null ? RecordingSessionFilter.ALL : sessions;
    }

    public HubScanFilter withSessions(RecordingSessionFilter newSessions) {
        return new HubScanFilter(hub, workspace, project, newSessions);
    }

    /**
     * A hub matches its id exactly, or its name loosely — so both columns {@code hubs_list} prints
     * work as input, and a reader who typed "production" is not asked for a UUID.
     */
    public boolean matches(HubInfo hubInfo) {
        if (hub == null) {
            return true;
        }
        return hub.equals(lower(hubInfo.hubId())) || contains(hubInfo.name(), hub);
    }

    public boolean matches(WorkspaceInfo workspaceInfo) {
        if (workspace == null) {
            return true;
        }
        return workspace.equals(lower(workspaceInfo.id()))
                || contains(workspaceInfo.name(), workspace)
                || contains(workspaceInfo.referenceId(), workspace);
    }

    public boolean matches(ProjectInfo projectInfo) {
        if (project == null) {
            return true;
        }
        return project.equals(lower(projectInfo.id()))
                || contains(projectInfo.name(), project)
                || contains(projectInfo.label(), project);
    }

    private static String normalise(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
