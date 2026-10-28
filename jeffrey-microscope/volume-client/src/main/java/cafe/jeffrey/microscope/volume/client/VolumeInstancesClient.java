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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.microscope.remote.client.InstancePage;
import cafe.jeffrey.microscope.remote.client.InstanceRead;
import cafe.jeffrey.microscope.remote.client.InstancesClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionPage;

import java.util.List;
import java.util.Optional;

/**
 * A project's instances off the volume, each its directory: one listing of the project. One
 * instance with a page of its sessions: one listing of the instance, whose names order the page.
 */
final class VolumeInstancesClient implements InstancesClient {

    private final VolumeSource source;

    VolumeInstancesClient(VolumeSource source) {
        this.source = source;
    }

    @Override
    public List<InstanceResponse> projectInstances(ProjectScope scope) {
        return source.read(() -> source.resolvedProject(scope).instances().stream()
                .map(VolumeResponses::instance)
                .toList());
    }

    @Override
    public Optional<InstanceRead> instance(ProjectScope scope, String instanceId, InstancePage page) {
        return source.read(() -> {
            ProjectTree project = source.resolvedProject(scope);
            return source.sessions(project).instance(instanceId, new SessionPage(page.limit(), page.before()))
                    .map(found -> new InstanceRead(
                            VolumeResponses.instance(found.instance()),
                            found.sessions().stream().map(VolumeResponses::header).toList(),
                            found.hasOlder()));
        });
    }
}
