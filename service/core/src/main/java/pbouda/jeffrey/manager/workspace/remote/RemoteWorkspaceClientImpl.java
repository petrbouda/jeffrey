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

package pbouda.jeffrey.manager.workspace.remote;

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.util.InstantUtils;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

public class RemoteWorkspaceClientImpl implements RemoteWorkspaceClient {

    private static final ParameterizedTypeReference<List<WorkspaceResponse>> WORKSPACE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String API_WORKSPACES = "/api/public/workspaces";
    private static final String API_WORKSPACES_ID = API_WORKSPACES + "/{id}";
    private static final String API_WORKSPACES_PROJECTS = API_WORKSPACES + "/{id}/projects";

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
        ResponseEntity<List<WorkspaceResponse>> entity;
        try {
            entity = handleResponse(uri, () -> {
                return restClient.get()
                        .uri(API_WORKSPACES)
                        .retrieve()
                        .toEntity(WORKSPACE_LIST_TYPE);
            });
        } catch (ResourceAccessException e) {
            throw new RemoteJeffreyOffline(uri, e);
        }

        return entity.getBody();
    }

    @Override
    public List<ProjectResponse> allProjects(String workspaceId) {
        ResponseEntity<List<ProjectResponse>> projects;
        try {
            projects = handleResponse(uri, () -> {
                return restClient.get()
                        .uri(API_WORKSPACES_PROJECTS, workspaceId)
                        .retrieve()
                        .toEntity(PROJECT_LIST_TYPE);
            });
        } catch (ResourceAccessException e) {
            throw new RemoteJeffreyOffline(uri, e);
        }

        return projects.getBody();
    }

    @Override
    public WorkspaceResult workspace(String workspaceId) {
        ResponseEntity<WorkspaceResponse> entity;
        try {
            entity = handleResponse(uri, () -> {
                return restClient.get()
                        .uri(API_WORKSPACES_ID, workspaceId)
                        .retrieve()
                        .toEntity(WorkspaceResponse.class);
            });
        } catch (ResourceAccessException e) {
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        } catch (RestClientException e) {
            return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
        }

        return WorkspaceResult.of(toWorkspaceInfo(uri, API_WORKSPACES_ID, entity.getBody()));
    }

    private static <T> ResponseEntity<T> handleResponse(URI uri, Supplier<ResponseEntity<T>> invocation) {
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();

        int statusCode = -1;
        try {
            return invocation.get();
        } catch (HttpStatusCodeException e) {
            statusCode = e.getStatusCode().value();
            throw e;
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = uri.getHost();
                event.remotePort = uri.getPort();
                event.method = HttpMethod.GET.name();
                event.mediaType = MediaType.APPLICATION_JSON_VALUE;
                event.status = statusCode;
                event.commit();
            }
        }
    }

    public static WorkspaceInfo toWorkspaceInfo(URI uri, String endpointPath, WorkspaceResponse response) {
        String relativePath = endpointPath.replace("{id}", response.id());
        return new WorkspaceInfo(
                response.id(),
                null,
                response.name(),
                response.description(),
                WorkspaceLocation.of(uri.resolve(relativePath)),
                WorkspaceLocation.of(uri),
                response.createdAt() != null ? InstantUtils.parseInstant(response.createdAt()) : null,
                WorkspaceType.REMOTE,
                WorkspaceStatus.AVAILABLE,
                response.projectCount());
    }
}
