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

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyUnavailableException;
import pbouda.jeffrey.platform.resources.request.FileDownloadRequest;
import pbouda.jeffrey.platform.resources.request.FilesDownloadRequest;
import pbouda.jeffrey.platform.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;
import pbouda.jeffrey.platform.resources.response.ProfilerSettingsResponse;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class RemoteWorkspaceClientImpl implements RemoteWorkspaceClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceClientImpl.class);

    private static final ParameterizedTypeReference<List<WorkspaceResponse>> WORKSPACE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<RecordingSessionResponse>> SESSION_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ImportantMessageResponse>> MESSAGE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String API_WORKSPACES = "/api/public/workspaces";
    private static final String API_WORKSPACES_ID = API_WORKSPACES + "/{workspaceId}";
    private static final String API_WORKSPACES_PROJECTS = API_WORKSPACES + "/{workspaceId}/projects";
    private static final String API_SESSION_STATISTICS = API_WORKSPACES_PROJECTS + "/{projectId}/repository/statistics";
    private static final String API_SESSIONS = API_WORKSPACES_PROJECTS + "/{projectId}/repository/sessions";
    private static final String API_SESSION = API_WORKSPACES_PROJECTS + "/{projectId}/repository/sessions/{sessionId}";
    private static final String API_DOWNLOAD_SELECTED_RECORDINGS = API_SESSION + "/recordings";
    private static final String API_DOWNLOAD_SELECTED_FILE = API_SESSION + "/artifact";
    private static final String API_PROFILER_SETTINGS = API_WORKSPACES_PROJECTS + "/{projectId}/profiler/settings";
    private static final String API_MESSAGES = API_WORKSPACES_PROJECTS + "/{projectId}/messages";
    private static final String API_ALERTS = API_MESSAGES + "/alerts";

    private final RestClient restClient;
    private final URI uri;

    public RemoteWorkspaceClientImpl(URI uri, RestClient.Builder restClientBuilder) {
        this.uri = uri;
        this.restClient = restClientBuilder
                .baseUrl(uri)
                .build();
    }

    @Override
    public List<WorkspaceResponse> allWorkspaces() {
        ResponseEntity<List<WorkspaceResponse>> entity = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_WORKSPACES)
                    .retrieve()
                    .toEntity(WORKSPACE_LIST_TYPE);
        });

        return entity.getBody();
    }

    @Override
    public List<ProjectResponse> allProjects(String workspaceId) {
        ResponseEntity<List<ProjectResponse>> projects = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_WORKSPACES_PROJECTS, workspaceId)
                    .retrieve()
                    .toEntity(PROJECT_LIST_TYPE);
        });

        return projects.getBody();
    }

    @Override
    public List<RecordingSessionResponse> recordingSessions(String workspaceId, String projectId) {
        ResponseEntity<List<RecordingSessionResponse>> recordingSessions = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_SESSIONS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(SESSION_LIST_TYPE);
        });

        return recordingSessions.getBody();
    }

    @Override
    public RecordingSessionResponse recordingSession(String workspaceId, String projectId, String sessionId) {
        ResponseEntity<RecordingSessionResponse> recordingSession = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_SESSION, workspaceId, projectId, sessionId)
                    .retrieve()
                    .toEntity(RecordingSessionResponse.class);
        });

        return recordingSession.getBody();
    }

    @Override
    public RepositoryStatisticsResponse repositoryStatistics(String workspaceId, String projectId) {
        ResponseEntity<RepositoryStatisticsResponse> statistics = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_SESSION_STATISTICS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(RepositoryStatisticsResponse.class);
        });

        return statistics.getBody();
    }

    @Override
    public void deleteSession(String workspaceId, String projectId, String sessionId) {
        invokeDelete(uri, () -> {
            return restClient.delete()
                    .uri(API_SESSION, workspaceId, projectId, sessionId)
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    @Override
    public WorkspaceResult workspace(String workspaceId) {
        try {
            ResponseEntity<WorkspaceResponse> entity = invokeGet(uri, () -> {
                return restClient.get()
                        .uri(API_WORKSPACES_ID, workspaceId)
                        .retrieve()
                        .toEntity(WorkspaceResponse.class);
            });

            return WorkspaceResult.of(toWorkspaceInfo(uri, API_WORKSPACES_ID, entity.getBody()));
        } catch (RemoteJeffreyUnavailableException e) {
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        } catch (RestClientException e) {
            return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
        }
    }

    @Override
    public CompletableFuture<Resource> downloadRecordings(
            String workspaceId, String projectId, String sessionId, List<String> recordingIds) {
        return CompletableFuture.supplyAsync(() -> {
            return invokePost(uri, () -> {
                return restClient.post()
                        .uri(API_DOWNLOAD_SELECTED_RECORDINGS, workspaceId, projectId, sessionId)
                        .body(new FilesDownloadRequest(recordingIds))
                        .retrieve()
                        .toEntity(Resource.class);
            }).getBody();
        }, Schedulers.sharedVirtual());
    }

    @Override
    public CompletableFuture<Resource> downloadFile(
            String workspaceId, String projectId, String sessionId, String fileId) {
        return CompletableFuture.supplyAsync(() -> {
            return invokePost(uri, () -> {
                return restClient.post()
                        .uri(API_DOWNLOAD_SELECTED_FILE, workspaceId, projectId, sessionId)
                        .body(new FileDownloadRequest(fileId))
                        .retrieve()
                        .toEntity(Resource.class);
            }).getBody();
        }, Schedulers.sharedVirtual());
    }

    @Override
    public EffectiveProfilerSettings fetchProfilerSettings(String workspaceId, String projectId) {
        ResponseEntity<ProfilerSettingsResponse> settings = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(API_PROFILER_SETTINGS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(ProfilerSettingsResponse.class);
        });

        ProfilerSettingsResponse body = settings.getBody();
        return body != null ? body.toModel() : EffectiveProfilerSettings.none();
    }

    @Override
    public void upsertProfilerSettings(String workspaceId, String projectId, String agentSettings) {
        invokePost(uri, () -> {
            return restClient.post()
                    .uri(API_PROFILER_SETTINGS, workspaceId, projectId)
                    .body(new ProfilerSettingsRequest(workspaceId, projectId, agentSettings))
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    @Override
    public void deleteProfilerSettings(String workspaceId, String projectId) {
        invokeDelete(uri, () -> {
            return restClient.delete()
                    .uri(API_PROFILER_SETTINGS, workspaceId, projectId)
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    @Override
    public List<ImportantMessageResponse> getMessages(String workspaceId, String projectId, Long start, Long end) {
        ResponseEntity<List<ImportantMessageResponse>> messages = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_MESSAGES)
                            .queryParamIfPresent("start", java.util.Optional.ofNullable(start))
                            .queryParamIfPresent("end", java.util.Optional.ofNullable(end))
                            .build(workspaceId, projectId))
                    .retrieve()
                    .toEntity(MESSAGE_LIST_TYPE);
        });

        return messages.getBody() != null ? messages.getBody() : List.of();
    }

    @Override
    public List<ImportantMessageResponse> getAlerts(String workspaceId, String projectId, Long start, Long end) {
        ResponseEntity<List<ImportantMessageResponse>> alerts = invokeGet(uri, () -> {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_ALERTS)
                            .queryParamIfPresent("start", java.util.Optional.ofNullable(start))
                            .queryParamIfPresent("end", java.util.Optional.ofNullable(end))
                            .build(workspaceId, projectId))
                    .retrieve()
                    .toEntity(MESSAGE_LIST_TYPE);
        });

        return alerts.getBody() != null ? alerts.getBody() : List.of();
    }

    private static <T> ResponseEntity<T> invokeGet(URI uri, Supplier<ResponseEntity<T>> invocation) {
        return invoke(uri, HttpMethod.GET, invocation);
    }

    private static <T> ResponseEntity<T> invokePost(URI uri, Supplier<ResponseEntity<T>> invocation) {
        return invoke(uri, HttpMethod.POST, invocation);
    }

    private static void invokeDelete(URI uri, Supplier<ResponseEntity<Void>> invocation) {
        invoke(uri, HttpMethod.DELETE, invocation);
    }

    private static <T> ResponseEntity<T> invoke(URI uri, HttpMethod method, Supplier<ResponseEntity<T>> invocation) {
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();

        int statusCode = -1;
        try {
            return invocation.get();
        } catch (ResourceAccessException e) {
            throw Exceptions.remoteJeffreyUnavailable(uri, e);
        } catch (HttpStatusCodeException e) {
            statusCode = e.getStatusCode().value();
            ErrorResponse responseError = e.getResponseBodyAs(ErrorResponse.class);
            LOG.warn("Remote Jeffrey returned an error: uri={} error={}", uri, responseError);
            throw Exceptions.fromErrorResponse(responseError);
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = uri.getHost();
                event.remotePort = uri.getPort();
                event.method = method.name();
                event.mediaType = MediaType.APPLICATION_JSON_VALUE;
                event.status = statusCode;
                event.commit();
            }
        }
    }

    public static WorkspaceInfo toWorkspaceInfo(URI uri, String endpointPath, WorkspaceResponse response) {
        String relativePath = endpointPath.replace("{workspaceId}", response.id());
        return new WorkspaceInfo(
                null,
                response.id(),
                null,
                response.name(),
                response.description(),
                WorkspaceLocation.of(uri.resolve(relativePath)),
                WorkspaceLocation.of(uri),
                Instant.ofEpochMilli(response.createdAt()),
                WorkspaceType.REMOTE,
                WorkspaceStatus.AVAILABLE,
                response.projectCount());
    }
}
