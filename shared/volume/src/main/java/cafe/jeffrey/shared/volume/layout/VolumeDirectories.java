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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a level of the volume is: the visible directories one listing of its parent names, and
 * whether one directory is there. Dot-directories are the volume's own bookkeeping. A listing or a
 * look that fails reads as nothing there, logged, so one unreadable directory leaves only its own
 * subtree out.
 */
public final class VolumeDirectories {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeDirectories.class);

    private static final Comparator<VolumeEntry> BY_NAME = Comparator.comparing(VolumeEntry::name);

    private VolumeDirectories() {
    }

    /** The visible child directories as one listing names them, sorted by name; none when unlistable. */
    public static List<VolumeEntry> childDirectoryEntries(VolumeAccess volume, VolumePath parent) {
        try {
            return volume.list(parent).stream()
                    .filter(VolumeEntry::isDirectory)
                    .filter(entry -> !entry.path().isHidden())
                    .sorted(BY_NAME)
                    .toList();
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot list directory: path={}", parent, e);
            return List.of();
        }
    }

    /** The visible child directories, sorted by name. */
    public static List<VolumePath> childDirectories(VolumeAccess volume, VolumePath parent) {
        return childDirectoryEntries(volume, parent).stream()
                .map(VolumeEntry::path)
                .toList();
    }

    /** The directory at {@code path} as one look at it describes it; empty when no directory is there. */
    public static Optional<VolumeEntry> directory(VolumeAccess volume, VolumePath path) {
        try {
            return volume.stat(path).filter(VolumeEntry::isDirectory);
        } catch (UncheckedIOException e) {
            LOG.debug("Cannot look at a directory, reading it as not there: path={} reason={}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
