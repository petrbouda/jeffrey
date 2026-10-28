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
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A directory at the volume's root read as a workspace: named by the hub's
 * {@code .workspace-info.json} when one describes it, by its id otherwise — the provisioner writes
 * into a workspace before anyone describes it. Every reader of a volume names a workspace this way.
 *
 * @param path      the directory, named by the workspace's id
 * @param createdAt the marker's time, or the directory's own; empty when neither can be read
 * @param described whether the hub's marker describes it; false for a workspace only listed
 */
public record VolumeWorkspace(VolumePath path, String name, Optional<Instant> createdAt, boolean described) {

    public VolumeWorkspace {
        if (path == null || name == null || createdAt == null) {
            throw new IllegalArgumentException("A workspace needs its directory, a name and its creation time");
        }
    }

    public String id() {
        return path.name();
    }

    /** The workspace in this directory, read now. */
    public static VolumeWorkspace at(VolumeAccess volume, VolumePath directory) {
        Optional<WorkspaceMarker> marker =
                VolumeMarkers.read(volume, directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class);
        String name = marker.map(WorkspaceMarker::name).orElse(directory.name());
        Optional<Instant> createdAt = marker.map(found -> Instant.ofEpochMilli(found.createdAt()))
                .or(() -> volume.stat(directory).flatMap(VolumeEntry::createdAt));
        return new VolumeWorkspace(directory, name, createdAt, marker.isPresent());
    }

    /**
     * Every visible directory at the volume's root, read as a workspace: each marker read, which is
     * what telling a workspace's name from its id takes. For the rare change that must know every
     * name; a list shows {@link #listedAt} instead.
     */
    public static List<VolumeWorkspace> all(VolumeAccess volume) {
        return VolumeMarkers.childDirectories(volume, VolumePath.root()).stream()
                .map(directory -> at(volume, directory))
                .toList();
    }

    /**
     * Every visible directory at the volume's root as one listing names it: by its id, created
     * when the listing says, no marker read.
     */
    public static List<VolumeWorkspace> listedAt(VolumeAccess volume) {
        return VolumeMarkers.childDirectoryEntries(volume, VolumePath.root()).stream()
                .sorted(Comparator.comparing(VolumeEntry::name))
                .map(entry -> new VolumeWorkspace(entry.path(), entry.name(), entry.createdAt(), false))
                .toList();
    }
}
