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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import pbouda.jeffrey.platform.resources.pub.PublicApiPaths;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;

import java.util.List;

public class RemoteRepositoryClientImpl implements RemoteRepositoryClient {

    private static final ParameterizedTypeReference<List<RecordingSessionResponse>> SESSION_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteHttpInvoker invoker;

    public RemoteRepositoryClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public List<RecordingSessionResponse> recordingSessions(String workspaceId, String projectId) {
        ResponseEntity<List<RecordingSessionResponse>> recordingSessions = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.SESSIONS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(SESSION_LIST_TYPE);
        });

        return recordingSessions.getBody();
    }

    @Override
    public RecordingSessionResponse recordingSession(String workspaceId, String projectId, String sessionId) {
        ResponseEntity<RecordingSessionResponse> recordingSession = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.SESSION, workspaceId, projectId, sessionId)
                    .retrieve()
                    .toEntity(RecordingSessionResponse.class);
        });

        return recordingSession.getBody();
    }

    @Override
    public RepositoryStatisticsResponse repositoryStatistics(String workspaceId, String projectId) {
        ResponseEntity<RepositoryStatisticsResponse> statistics = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.REPOSITORY_STATISTICS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(RepositoryStatisticsResponse.class);
        });

        return statistics.getBody();
    }

    @Override
    public void deleteSession(String workspaceId, String projectId, String sessionId) {
        invoker.delete(() -> {
            return invoker.restClient().delete()
                    .uri(PublicApiPaths.SESSION, workspaceId, projectId, sessionId)
                    .retrieve()
                    .toBodilessEntity();
        });
    }
}
