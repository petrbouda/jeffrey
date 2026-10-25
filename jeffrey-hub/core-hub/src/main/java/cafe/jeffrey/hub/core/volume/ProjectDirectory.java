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
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.ProjectTree;

import java.time.Instant;
import java.util.Optional;

/**
 * A project directory on the volume and the hub's project it declares.
 *
 * @param tree the directory as the volume's layout reads it; where the project's instances and
 *             sessions lie
 */
public record ProjectDirectory(ProjectTree tree, ProjectInfo info) {

    public ProjectDirectory {
        if (tree == null || info == null) {
            throw new IllegalArgumentException("A project directory needs its tree and its project");
        }
    }

    /**
     * The project in this directory, or empty when it is not one — {@link ProjectTree#at} is the
     * one test, shared by the listing and the workspace's count.
     */
    public static Optional<ProjectDirectory> at(VolumeAccess volume, VolumePath directory, String workspaceId) {
        return ProjectTree.at(volume, directory)
                .map(tree -> new ProjectDirectory(tree, new ProjectInfo(
                        tree.marker().projectId(),
                        tree.marker().projectName(),
                        workspaceId,
                        Instant.ofEpochMilli(tree.marker().createdAt()),
                        tree.marker().attributes())));
    }
}
