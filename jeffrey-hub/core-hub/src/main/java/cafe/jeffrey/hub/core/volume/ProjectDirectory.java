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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A project directory on the volume and what its {@code .project-info.json} declares.
 *
 * @param path the directory the marker was found in; where the project's instances and sessions lie
 */
public record ProjectDirectory(Path path, ProjectInfo info) {

    public ProjectDirectory {
        if (path == null || info == null) {
            throw new IllegalArgumentException("A project directory needs a path and its project");
        }
    }

    /**
     * The project in this directory, or empty when it holds no readable marker naming a project
     * id — the one test of "is this a project", shared by the listing and the workspace's count.
     */
    public static Optional<ProjectDirectory> at(Path directory, String workspaceId) {
        return VolumeMarkers.read(directory, JeffreyLayout.PROJECT_INFO_FILE, RemoteProject.class)
                .filter(marker -> marker.projectId() != null && !marker.projectId().isBlank())
                .map(marker -> new ProjectDirectory(directory, new ProjectInfo(
                        marker.projectId(),
                        marker.projectName(),
                        workspaceId,
                        Instant.ofEpochMilli(marker.createdAt()),
                        marker.attributes())));
    }
}
