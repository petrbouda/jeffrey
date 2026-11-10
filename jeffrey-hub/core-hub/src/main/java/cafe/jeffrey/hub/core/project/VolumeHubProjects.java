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
package cafe.jeffrey.hub.core.project;

import cafe.jeffrey.hub.core.project.repository.FilesystemRepositoryStorage;
import cafe.jeffrey.hub.core.volume.MountTimes;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeWorkspace;

import java.util.List;

/**
 * The projects on the hub's mount. A workspace is a directory at the root named by a valid
 * reference id, which the provisioner creates; a project is a visible directory in it, named by
 * the project name the provisioner was configured with. Nothing in a directory is read to tell —
 * the same rule Microscope reads the volume by — and the hub creates none. One listing of the
 * root, then one of each workspace.
 */
public final class VolumeHubProjects implements HubProjects {

    private final FilesystemVolume volume;

    public VolumeHubProjects(FilesystemVolume volume) {
        this.volume = volume;
    }

    @Override
    public List<HubProject> all() {
        return VolumeWorkspace.all(volume).stream()
                .filter(workspace -> WorkspaceReferenceId.isValid(workspace.id()))
                .flatMap(workspace -> ProjectTree.allIn(volume, workspace.path()).stream()
                        .map(tree -> project(tree, workspace.id())))
                .toList();
    }

    private HubProject project(ProjectTree tree, String workspaceId) {
        ProjectInfo info = new ProjectInfo(
                tree.id(), workspaceId, MountTimes.createdAt(tree.createdAt(), tree.path()));
        return new HubProject(info, new FilesystemRepositoryStorage(info, tree, volume));
    }
}
