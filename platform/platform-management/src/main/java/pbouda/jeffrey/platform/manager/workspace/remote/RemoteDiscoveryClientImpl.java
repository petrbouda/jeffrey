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
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.response.PublicApiInfoResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyUnavailableException;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public class RemoteDiscoveryClientImpl implements RemoteDiscoveryClient {

    private static final ParameterizedTypeReference<List<WorkspaceResponse>> WORKSPACE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteHttpInvoker invoker;

    public RemoteDiscoveryClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public PublicApiInfoResponse info() {
        ResponseEntity<PublicApiInfoResponse> entity = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.INFO)
                    .retrieve()
                    .toEntity(PublicApiInfoResponse.class);
        });

        return entity.getBody();
    }

    @Override
    public List<WorkspaceResponse> allWorkspaces() {
        ResponseEntity<List<WorkspaceResponse>> entity = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.WORKSPACES)
                    .retrieve()
                    .toEntity(WORKSPACE_LIST_TYPE);
        });

        return entity.getBody();
    }

    @Override
    public WorkspaceResult workspace(String workspaceId) {
        try {
            ResponseEntity<WorkspaceResponse> entity = invoker.get(() -> {
                return invoker.restClient().get()
                        .uri(PublicApiPaths.WORKSPACE, workspaceId)
                        .retrieve()
                        .toEntity(WorkspaceResponse.class);
            });

            return WorkspaceResult.of(toWorkspaceInfo(invoker.uri(), entity.getBody()));
        } catch (RemoteJeffreyUnavailableException e) {
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        } catch (Exception e) {
            return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
        }
    }

    @Override
    public List<ProjectResponse> allProjects(String workspaceId) {
        ResponseEntity<List<ProjectResponse>> projects = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.PROJECTS, workspaceId)
                    .retrieve()
                    .toEntity(PROJECT_LIST_TYPE);
        });

        return projects.getBody();
    }

    static WorkspaceInfo toWorkspaceInfo(URI uri, WorkspaceResponse response) {
        String relativePath = PublicApiPaths.WORKSPACE.replace("{workspaceId}", response.id());
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
