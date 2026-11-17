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
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.ProjectTree;

import java.nio.file.Path;

/**
 * A project laid down with {@code VolumeWriter} (the provisioner's and the profiler's layout),
 * read back as the jobs read it.
 */
public final class ProjectVolumeFixture {

    private ProjectVolumeFixture() {
    }

    /** The project in {@code projectDir}, on the volume whose workspaces lie in {@code workspacesRoot}. */
    public static VolumeProject project(ProjectRef ref, Path workspacesRoot, Path projectDir) {
        FilesystemVolume volume = new FilesystemVolume(workspacesRoot);
        ProjectTree tree = ProjectTree.at(volume, volumePath(workspacesRoot, projectDir)).orElseThrow();
        return new VolumeProject(ref, tree, volume);
    }

    /** Where {@code path}, a directory under {@code root}, lies on the volume rooted there. */
    private static VolumePath volumePath(Path root, Path path) {
        VolumePath volumePath = VolumePath.root();
        for (Path element : root.relativize(path)) {
            volumePath = volumePath.resolve(element.toString());
        }
        return volumePath;
    }
}
