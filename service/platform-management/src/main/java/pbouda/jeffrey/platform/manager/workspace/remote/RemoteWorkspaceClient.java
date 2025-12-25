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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.core.io.Resource;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface RemoteWorkspaceClient {

    @FunctionalInterface
    interface Factory extends Function<URI, RemoteWorkspaceClient> {
    }

    record WorkspaceResult(WorkspaceInfo info, WorkspaceStatus status) {
        public static WorkspaceResult of(WorkspaceStatus status) {
            return new WorkspaceResult(null, status);
        }

        public static WorkspaceResult of(WorkspaceInfo info) {
            return new WorkspaceResult(info, WorkspaceStatus.AVAILABLE);
        }
    }

    List<WorkspaceResponse> allWorkspaces();

    List<ProjectResponse> allProjects(String workspaceId);

    List<RecordingSessionResponse> recordingSessions(String workspaceId, String projectId);

    RecordingSessionResponse recordingSession(String workspaceId, String projectId, String sessionId);

    RepositoryStatisticsResponse  repositoryStatistics(String workspaceId, String projectId);

    void deleteSession(String workspaceId, String projectId, String sessionId);

    WorkspaceResult workspace(String workspaceId);

    CompletableFuture<Resource> downloadRecordings(
            String workspaceId, String projectId, String sessionId, List<String> recordingIds);

    CompletableFuture<Resource> downloadFile(
            String workspaceId, String projectId, String sessionId, String fileId);
}
