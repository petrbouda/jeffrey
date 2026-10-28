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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A workspace: a visible directory at the volume's root, named by its id — the reference id the
 * provisioner writes into, which is also its name. Nothing in it is read to tell, like every
 * level below it.
 *
 * @param path      the directory, named by the workspace's id
 * @param createdAt when the directory was created, as the volume lists it; empty on a share that
 *                  lists no times
 */
public record VolumeWorkspace(VolumePath path, Optional<Instant> createdAt) {

    public VolumeWorkspace {
        if (path == null || createdAt == null) {
            throw new IllegalArgumentException("A workspace needs its directory and its creation time");
        }
    }

    public String id() {
        return path.name();
    }

    /** Every workspace on the volume: one listing of its root. */
    public static List<VolumeWorkspace> all(VolumeAccess volume) {
        return VolumeDirectories.childDirectoryEntries(volume, VolumePath.root()).stream()
                .map(entry -> new VolumeWorkspace(entry.path(), entry.createdAt()))
                .toList();
    }

    /**
     * The workspace by that id, or empty when no directory is there or the id is not one plain
     * path element: one look at it.
     */
    public static Optional<VolumeWorkspace> at(VolumeAccess volume, String id) {
        return VolumePath.root().visibleChild(id)
                .flatMap(directory -> VolumeDirectories.directory(volume, directory)
                        .map(entry -> new VolumeWorkspace(directory, entry.createdAt())));
    }
}
