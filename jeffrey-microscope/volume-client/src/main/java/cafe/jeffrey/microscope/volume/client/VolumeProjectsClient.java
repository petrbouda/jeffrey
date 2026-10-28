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

import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.ProjectsClient;
import cafe.jeffrey.shared.volume.WritableVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deleting a project is removing its directory, everything the provisioner wrote under it included. */
final class VolumeProjectsClient implements ProjectsClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeProjectsClient.class);

    private final VolumeSource source;

    VolumeProjectsClient(VolumeSource source) {
        this.source = source;
    }

    @Override
    public void deleteProject(ProjectScope scope) {
        WritableVolume volume = source.writable("Cannot delete project " + scope.projectId());
        source.read(() -> {
            volume.delete(source.resolvedProject(scope).path());
            LOG.info("Deleted project on the recording volume: project_id={} source={}",
                    scope.projectId(), source.description());
        });
    }
}
