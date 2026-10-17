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

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A workspace directory on the volume and what its {@code .workspace-info.json} declares (or, for
 * an auto-created one without it, its id and the directory's own creation time).
 *
 * @param path the directory, named by the workspace's id
 */
public record WorkspaceDirectory(Path path, String name, Instant createdAt) {

    public WorkspaceDirectory {
        if (path == null || name == null || createdAt == null) {
            throw new IllegalArgumentException("A workspace directory needs a path, a name and a creation time");
        }
    }

    public String id() {
        return path.getFileName().toString();
    }

    /** Its projects, as {@link ProjectDirectory#at} recognises them — read now. */
    public List<ProjectDirectory> projects() {
        return VolumeMarkers.childDirectories(path).stream()
                .map(directory -> ProjectDirectory.at(directory, id()))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The workspace with its project count, which reads every project's marker — so only a
     * caller that shows the count asks for it, not every request that merely names the workspace.
     */
    public WorkspaceInfo info() {
        return new WorkspaceInfo(id(), name, createdAt, WorkspaceStatus.AVAILABLE, projects().size());
    }
}
