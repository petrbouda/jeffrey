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
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.ProjectTree;

import java.time.Instant;
import java.util.List;

/**
 * A workspace directory on the volume, named by its id — which is all a workspace is.
 *
 * @param volume    the volume the workspace lies on
 * @param path      the directory, named by the workspace's id
 * @param createdAt when the directory was created
 */
public record WorkspaceDirectory(VolumeAccess volume, VolumePath path, Instant createdAt) {

    public WorkspaceDirectory {
        if (volume == null || path == null || createdAt == null) {
            throw new IllegalArgumentException("A workspace directory needs a volume, a path and a creation time");
        }
    }

    public String id() {
        return path.name();
    }

    /** Its projects, as {@link ProjectTree#at} recognises them — read now. */
    public List<ProjectDirectory> projects() {
        return ProjectTree.allIn(volume, path).stream()
                .map(tree -> ProjectDirectory.of(tree, id()))
                .toList();
    }

    /**
     * The workspace with its project count: one listing of its directory, so only a caller that
     * shows the count asks for it, not every request that merely names the workspace.
     */
    public WorkspaceInfo info() {
        return new WorkspaceInfo(id(), createdAt, projects().size());
    }
}
