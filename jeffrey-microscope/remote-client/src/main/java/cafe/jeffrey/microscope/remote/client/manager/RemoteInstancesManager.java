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

package cafe.jeffrey.microscope.remote.client.manager;

import cafe.jeffrey.microscope.remote.client.InstancePage;
import cafe.jeffrey.microscope.remote.client.InstancesClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionHeaderResponse;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.InstantUtils;

import java.util.List;
import java.util.Optional;

/**
 * A project's instances, one level of the remote's tree per call: the project's instances from
 * {@link InstancesClient#projectInstances}, one instance with a page of its sessions from
 * {@link InstancesClient#instance}.
 */
public class RemoteInstancesManager {

    private final ProjectScope scope;
    private final InstancesClient instancesClient;

    public RemoteInstancesManager(ProjectScope scope, InstancesClient instancesClient) {
        this.scope = scope;
        this.instancesClient = instancesClient;
    }

    public List<ProjectInstanceInfo> findAll() {
        return instancesClient.projectInstances(scope).stream()
                .map(this::toProjectInstanceInfo)
                .toList();
    }

    /**
     * One instance with a page of its sessions, newest first, each named but not read, from one
     * read of the remote; empty when the project holds no instance by that id.
     *
     * @param before the session the page starts after, or empty for the newest
     */
    public Optional<InstanceWithSessions> find(String instanceId, Optional<String> before) {
        return instancesClient.instance(scope, instanceId, InstancePage.newest(before))
                .map(found -> new InstanceWithSessions(
                        toProjectInstanceInfo(found.instance()),
                        found.sessions().stream()
                                .map(RecordingSessionHeaderResponse::toHeader)
                                .toList(),
                        found.hasOlder()));
    }

    private ProjectInstanceInfo toProjectInstanceInfo(InstanceResponse response) {
        return new ProjectInstanceInfo(
                response.id(),
                scope.projectId(),
                response.instanceName(),
                InstantUtils.fromEpochMilli(response.createdAt()));
    }
}
