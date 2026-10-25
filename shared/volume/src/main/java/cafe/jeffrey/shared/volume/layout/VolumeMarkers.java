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

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reading the marker files that make the volume its own state: the provisioner's
 * {@code .project-info.json}, {@code .instance-info.json} and {@code .session-info.json}, the
 * hub's {@code .workspace-info.json}, and the flag files beside them. Writing them is the writer's
 * business, never a reader's.
 */
public final class VolumeMarkers {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeMarkers.class);

    private static final Comparator<VolumePath> BY_NAME = Comparator.comparing(VolumePath::name);

    private VolumeMarkers() {
    }

    /**
     * The marker in {@code directory}, or empty when there is none or it cannot be parsed — a
     * partially written or foreign file leaves its subtree out until it reads cleanly.
     */
    public static <T> Optional<T> read(VolumeAccess volume, VolumePath directory, String markerFileName, Class<T> type) {
        VolumePath markerFile = directory.resolve(markerFileName);
        String content;
        try {
            Optional<byte[]> bytes = volume.read(markerFile);
            if (bytes.isEmpty()) {
                return Optional.empty();
            }
            content = new String(bytes.get(), StandardCharsets.UTF_8);
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot read marker file, leaving its directory out: file={}", markerFile, e);
            return Optional.empty();
        }
        try {
            return Optional.of(Json.read(content, type));
        } catch (RuntimeException e) {
            LOG.warn("Cannot parse marker file, leaving its directory out: file={} reason={}",
                    markerFile, e.getMessage());
            return Optional.empty();
        }
    }

    /** Whether an empty flag file is set; one that cannot be looked at reads as not set. */
    public static boolean hasFlag(VolumeAccess volume, VolumePath directory, String flagFileName) {
        VolumePath flag = directory.resolve(flagFileName);
        try {
            return volume.stat(flag).isPresent();
        } catch (UncheckedIOException e) {
            LOG.debug("Cannot look at a flag file, reading it as not set: file={} reason={}", flag, e.getMessage());
            return false;
        }
    }

    /** The visible child directories, sorted by name; dot-directories are the volume's own bookkeeping. */
    public static List<VolumePath> childDirectories(VolumeAccess volume, VolumePath parent) {
        try {
            return volume.list(parent).stream()
                    .filter(VolumeEntry::isDirectory)
                    .map(VolumeEntry::path)
                    .filter(path -> !path.isHidden())
                    .sorted(BY_NAME)
                    .toList();
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot list directory: path={}", parent, e);
            return List.of();
        }
    }

    /** Whether a directory is there; one that cannot be looked at reads as not there. */
    public static boolean isDirectory(VolumeAccess volume, VolumePath path) {
        try {
            return volume.stat(path).map(VolumeEntry::isDirectory).orElse(false);
        } catch (UncheckedIOException e) {
            LOG.debug("Cannot look at a directory, reading it as not there: path={} reason={}", path, e.getMessage());
            return false;
        }
    }
}
