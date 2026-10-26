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

import cafe.jeffrey.microscope.remote.client.InstanceRead;
import cafe.jeffrey.microscope.remote.client.InstancesClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.shared.volume.layout.ProjectTree;

import java.util.List;
import java.util.Optional;

/**
 * A project's instances off the volume, each read off its newest session; one instance with its
 * sessions, each with its file count and size but not its files.
 */
final class VolumeInstancesClient implements InstancesClient {

    private final VolumeSource source;

    VolumeInstancesClient(VolumeSource source) {
        this.source = source;
    }

    @Override
    public List<InstanceResponse> projectInstances(ProjectScope scope) {
        return source.read(() -> source.sessions(source.requireProject(scope)).instances().stream()
                .map(VolumeResponses::instance)
                .toList());
    }

    @Override
    public Optional<InstanceRead> instance(ProjectScope scope, String instanceId) {
        return source.read(() -> {
            ProjectTree project = source.requireProject(scope);
            return source.sessions(project).instance(instanceId).map(found -> new InstanceRead(
                    VolumeResponses.instance(found.summary()),
                    found.sessions().stream()
                            .map(session -> VolumeResponses.header(session, project.files(session)))
                            .toList()));
        });
    }
}
