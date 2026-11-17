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

package cafe.jeffrey.volumescheduler.volume;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeWorkspace;

import java.util.List;

/**
 * Every project on the recording volume: each project directory of each workspace, read with the
 * same layout rules Microscope reads the volume by ({@link VolumeWorkspace}, {@link ProjectTree}).
 * Nothing in a directory is read to tell, nothing is remembered, and nothing is created: one
 * listing of the root, then one of each workspace, every time.
 */
public final class VolumeProjects {

    private final FilesystemVolume volume;

    public VolumeProjects(FilesystemVolume volume) {
        this.volume = volume;
    }

    public List<VolumeProject> all() {
        return VolumeWorkspace.all(volume).stream()
                .flatMap(workspace -> ProjectTree.allIn(volume, workspace.path()).stream()
                        .map(tree -> new VolumeProject(new ProjectRef(workspace.id(), tree.id()), tree, volume)))
                .toList();
    }
}
