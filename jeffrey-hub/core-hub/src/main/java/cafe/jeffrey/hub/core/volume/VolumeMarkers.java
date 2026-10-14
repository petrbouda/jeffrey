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

import cafe.jeffrey.shared.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Reading and writing the marker files that make the volume the hub's only state: the
 * provisioner's {@code .project-info.json}, {@code .instance-info.json} and
 * {@code .session-info.json}, and the hub's own {@code .workspace-info.json} and {@code .retained}.
 */
public final class VolumeMarkers {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeMarkers.class);

    private static final String HIDDEN_PREFIX = ".";
    private static final String SCRATCH_SUFFIX = ".tmp";

    private VolumeMarkers() {
    }

    /**
     * The marker in {@code directory}, or empty when there is none or it cannot be parsed — a
     * partially written or foreign file leaves its subtree out until it reads cleanly.
     */
    public static <T> Optional<T> read(Path directory, String markerFileName, Class<T> type) {
        Path markerFile = directory.resolve(markerFileName);
        String content;
        try {
            content = Files.readString(markerFile);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
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

    /** Writes a marker whole or not at all: to a scratch file beside it, then renamed onto it. */
    public static void write(Path directory, String markerFileName, Object marker) {
        Path target = directory.resolve(markerFileName);
        Path scratch = directory.resolve(HIDDEN_PREFIX + markerFileName + "." + UUID.randomUUID() + SCRATCH_SUFFIX);
        try {
            Files.writeString(scratch, Json.toString(marker));
            try {
                Files.move(scratch, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(scratch, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write marker file: file=" + target, e);
        } finally {
            deleteQuietly(scratch);
        }
    }

    /** Creates or removes an empty flag file. */
    public static void setFlag(Path directory, String flagFileName, boolean present) {
        Path flag = directory.resolve(flagFileName);
        try {
            if (present) {
                if (!Files.exists(flag)) {
                    Files.createFile(flag);
                }
            } else {
                Files.deleteIfExists(flag);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update flag file: file=" + flag, e);
        }
    }

    public static boolean hasFlag(Path directory, String flagFileName) {
        return Files.exists(directory.resolve(flagFileName));
    }

    /** The visible child directories, sorted by name; dot-directories are the volume's own bookkeeping. */
    public static List<Path> childDirectories(Path parent) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(parent)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith(HIDDEN_PREFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot list directory: path={}", parent, e);
            return List.of();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("Cannot remove scratch file: path={}", path, e);
        }
    }
}
