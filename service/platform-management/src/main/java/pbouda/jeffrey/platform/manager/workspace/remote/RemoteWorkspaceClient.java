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
import pbouda.jeffrey.shared.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;
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

    /**
     * Fetches the effective profiler settings for the given project,
     * resolved from the hierarchy: project > workspace > global.
     */
    EffectiveProfilerSettings fetchProfilerSettings(String workspaceId, String projectId);

    /**
     * Upserts profiler settings for the given project.
     */
    void upsertProfilerSettings(String workspaceId, String projectId, String agentSettings);

    /**
     * Deletes profiler settings for the given project.
     */
    void deleteProfilerSettings(String workspaceId, String projectId);

    /**
     * Fetches important messages for the given project within an optional time range.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param start       start time in epoch milliseconds (optional)
     * @param end         end time in epoch milliseconds (optional)
     * @return list of important messages
     */
    List<ImportantMessageResponse> getMessages(String workspaceId, String projectId, Long start, Long end);

    /**
     * Fetches alert messages for the given project within an optional time range.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param start       start time in epoch milliseconds (optional)
     * @param end         end time in epoch milliseconds (optional)
     * @return list of alert messages
     */
    List<ImportantMessageResponse> getAlerts(String workspaceId, String projectId, Long start, Long end);
}
