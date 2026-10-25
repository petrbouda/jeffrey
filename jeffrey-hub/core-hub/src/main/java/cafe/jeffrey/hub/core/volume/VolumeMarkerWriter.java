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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Writing the marker files the hub owns on the volume: its {@code .workspace-info.json} and the
 * {@code .retained} flag. Reading them, and the provisioner's markers, is the shared layout's
 * business ({@code VolumeMarkers} in {@code shared/volume}).
 */
public final class VolumeMarkerWriter {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeMarkerWriter.class);

    private static final String HIDDEN_PREFIX = ".";
    private static final String SCRATCH_SUFFIX = ".tmp";

    private VolumeMarkerWriter() {
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

    /** Creates or removes an empty flag file; setting a flag that is already set is not an error. */
    public static void setFlag(Path directory, String flagFileName, boolean present) {
        Path flag = directory.resolve(flagFileName);
        try {
            if (present) {
                createIfAbsent(flag);
            } else {
                Files.deleteIfExists(flag);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update flag file: file=" + flag, e);
        }
    }

    private static void createIfAbsent(Path flag) throws IOException {
        try {
            Files.createFile(flag);
        } catch (FileAlreadyExistsException e) {
            // Set already, by an earlier call or one running alongside this one
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
