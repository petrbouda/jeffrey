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
import cafe.jeffrey.shared.volume.layout.ProjectTree;


/**
 * A project directory on the volume and the hub's project it names.
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

    /** The hub's project the directory names, in workspace {@code workspaceId}. */
    public static ProjectDirectory of(ProjectTree tree, String workspaceId) {
        return new ProjectDirectory(tree, new ProjectInfo(tree.id(), tree.id(), workspaceId, tree.createdAt()));
    }
}
