/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager;

import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;
import pbouda.jeffrey.shared.common.model.time.TimeRange;

import java.time.Instant;
import java.util.List;

/**
 * Remote implementation of MessagesManager that calls the remote Jeffrey.
 * Used for REMOTE workspaces.
 */
public class RemoteMessagesManager implements MessagesManager {

    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final String workspaceId;
    private final String projectId;

    public RemoteMessagesManager(
            RemoteWorkspaceClient remoteWorkspaceClient,
            String workspaceId,
            String projectId) {

        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
    }

    @Override
    public List<ImportantMessage> getMessages(TimeRange timeRange) {
        Long start = toStartMillis(timeRange);
        Long end = toEndMillis(timeRange);

        return remoteWorkspaceClient.getMessages(workspaceId, projectId, start, end)
                .stream()
                .map(ImportantMessageResponse::toModel)
                .toList();
    }

    @Override
    public List<ImportantMessage> getAlerts(TimeRange timeRange) {
        Long start = toStartMillis(timeRange);
        Long end = toEndMillis(timeRange);

        return remoteWorkspaceClient.getAlerts(workspaceId, projectId, start, end)
                .stream()
                .map(ImportantMessageResponse::toModel)
                .toList();
    }

    private static Long toStartMillis(TimeRange timeRange) {
        if (timeRange instanceof AbsoluteTimeRange atr) {
            Instant start = atr.start();
            return start.equals(Instant.MIN) ? null : start.toEpochMilli();
        }
        return null;
    }

    private static Long toEndMillis(TimeRange timeRange) {
        if (timeRange instanceof AbsoluteTimeRange atr) {
            Instant end = atr.end();
            return end.equals(Instant.MAX) ? null : end.toEpochMilli();
        }
        return null;
    }
}
