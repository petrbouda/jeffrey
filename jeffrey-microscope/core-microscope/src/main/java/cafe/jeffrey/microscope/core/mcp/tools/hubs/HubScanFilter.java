/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

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
 * @param project   a fragment of a project name; {@code null} for every project
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
                || contains(projectInfo.name(), project);
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
