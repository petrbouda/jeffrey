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

package pbouda.jeffrey.manager.workspace.mirror;

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.workspace.WorkspaceMappers;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class MirroringWorkspaceClientImpl implements MirroringWorkspaceClient {

    private static final ParameterizedTypeReference<List<WorkspaceResponse>> WORKSPACE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String API_WORKSPACES = "/api/workspaces";
    private static final String API_WORKSPACES_ID = "/api/workspaces/{id}";
    private static final String API_WORKSPACES_PROJECTS = "/api/workspaces/{id}/projects";

    private final RestClient restClient;
    private final URI uri;

    public MirroringWorkspaceClientImpl(URI uri, RestClient.Builder restClientBuilder) {
        this.uri = uri;
        this.restClient = restClientBuilder
                .baseUrl(uri)
                .build();
    }

    @Override
    public List<WorkspaceInfo> allMirroringWorkspaces() {
        ResponseEntity<List<WorkspaceResponse>> entity = handleResponse(uri, () -> {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_WORKSPACES)
                            .queryParam("excludeMirrored", true)
                            .build()
                    )
                    .retrieve()
                    .toEntity(WORKSPACE_LIST_TYPE);
        });

        return entity.getBody().stream()
                .map(resp -> WorkspaceMappers.toWorkspaceInfo(uri, API_WORKSPACES_ID, resp))
                .toList();
    }

    @Override
    public List<ProjectResponse> allProjects(String workspaceId) {
        ResponseEntity<List<ProjectResponse>> projects = handleResponse(uri, () -> {
            return restClient.get()
                    .uri(API_WORKSPACES_PROJECTS, workspaceId)
                    .retrieve()
                    .toEntity(PROJECT_LIST_TYPE);
        });

        return projects.getBody();
    }

    @Override
    public Optional<WorkspaceInfo> mirroringWorkspace(String workspaceId) {
        ResponseEntity<WorkspaceResponse> entity = handleResponse(uri, () -> {
            return restClient.get()
                    .uri(API_WORKSPACES_ID, workspaceId)
                    .retrieve()
                    .toEntity(WorkspaceResponse.class);
        });

        HttpStatusCode statusCode = entity.getStatusCode();
        if (statusCode.is2xxSuccessful()) {
            return Optional.of(WorkspaceMappers.toWorkspaceInfo(uri, API_WORKSPACES_ID, entity.getBody()));
        } else {
            return Optional.empty();
        }
    }

    private static <T> ResponseEntity<T> handleResponse(URI uri, Supplier<ResponseEntity<T>> invocation) {
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();
        ResponseEntity<T> entity = invocation.get();

        event.end();
        if (event.shouldCommit()) {
            event.remoteHost = uri.getHost();
            event.remotePort = uri.getPort();
            event.method = HttpMethod.GET.name();
            event.mediaType = MediaType.APPLICATION_JSON_VALUE;
            event.status = entity.getStatusCode().value();
            event.commit();
        }

        if (!entity.getStatusCode().is2xxSuccessful() || entity.getBody() == null) {
            throw new IllegalStateException("Failed to fetch workspaces from " + uri);
        }

        return entity;
    }
}
