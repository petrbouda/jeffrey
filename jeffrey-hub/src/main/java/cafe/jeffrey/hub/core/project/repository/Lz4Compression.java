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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.volume.VolumePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * LZ4 appended to the file's name ({@code profile-1.jfr} → {@code profile-1.jfr.lz4}). Written to
 * a hidden scratch file beside the target and renamed onto it, so an archive under its own name is
 * always complete.
 */
final class Lz4Compression implements Compression {

    private static final Logger LOG = LoggerFactory.getLogger(Lz4Compression.class);

    private static final String SUFFIX = ".lz4";

    @Override
    public Path target(Path source) {
        return source.resolveSibling(source.getFileName() + SUFFIX);
    }

    @Override
    public Path compress(Path source, Path target) {
        Path scratch = scratch(target);
        try {
            Lz4Compressor.compress(source, scratch);

            // An empty archive is a silent failure; publishing it would let the recording be deleted
            if (Files.size(scratch) == 0) {
                throw new IllegalStateException(
                        "Compressing " + source + " produced no bytes; leaving the recording as it is");
            }
            return publish(scratch, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress file: source=" + source + " target=" + target, e);
        } finally {
            // A no-op after a successful rename; on failure it removes the partial scratch file
            removeScratch(scratch);
        }
    }

    /**
     * Renames the finished archive onto its name atomically; a filesystem without atomic moves
     * still gets the archive, with a warning since a reader may then see it partial.
     */
    private static Path publish(Path scratch, Path target) throws IOException {
        try {
            return Files.move(scratch, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.warn("Filesystem cannot rename atomically, so a reader may briefly see a partial archive: "
                    + "target={}", target);
            return Files.move(scratch, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The scratch file: beside the target so the rename stays on one filesystem, and unique per
     * attempt because two compression runs over one session can overlap. One left behind by a
     * killed hub is hidden and goes with its session directory.
     */
    private static Path scratch(Path target) {
        return target.resolveSibling(VolumePath.scratchName(target.getFileName().toString()));
    }

    private static void removeScratch(Path scratch) {
        try {
            Files.deleteIfExists(scratch);
        } catch (IOException e) {
            LOG.warn("Cannot remove the scratch file of a compression: scratch={}", scratch, e);
        }
    }
}
