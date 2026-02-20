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
import pbouda.jeffrey.platform.resources.response.InstanceResponse;
import pbouda.jeffrey.platform.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.platform.resources.pub.PublicApiPaths;

import java.util.List;

public class RemoteInstancesClientImpl implements RemoteInstancesClient {

    private static final ParameterizedTypeReference<List<InstanceResponse>> INSTANCE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<InstanceSessionResponse>> INSTANCE_SESSION_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteHttpInvoker invoker;

    public RemoteInstancesClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public List<InstanceResponse> projectInstances(String workspaceId, String projectId) {
        ResponseEntity<List<InstanceResponse>> instances = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.INSTANCES, workspaceId, projectId)
                    .retrieve()
                    .toEntity(INSTANCE_LIST_TYPE);
        });

        return instances.getBody() != null ? instances.getBody() : List.of();
    }

    @Override
    public InstanceResponse projectInstance(String workspaceId, String projectId, String instanceId) {
        ResponseEntity<InstanceResponse> instance = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.INSTANCE, workspaceId, projectId, instanceId)
                    .retrieve()
                    .toEntity(InstanceResponse.class);
        });

        return instance.getBody();
    }

    @Override
    public List<InstanceSessionResponse> projectInstanceSessions(
            String workspaceId, String projectId, String instanceId) {
        ResponseEntity<List<InstanceSessionResponse>> sessions = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.INSTANCE_SESSIONS, workspaceId, projectId, instanceId)
                    .retrieve()
                    .toEntity(INSTANCE_SESSION_LIST_TYPE);
        });

        return sessions.getBody() != null ? sessions.getBody() : List.of();
    }
}
