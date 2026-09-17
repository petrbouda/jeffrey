/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * LZ4, appended to the name the file already has, so {@code profile-1.jfr} becomes
 * {@code profile-1.jfr.lz4} — a name {@link HubManagedFile} classifies as JFR_LZ4, which
 * strips to the same id and carries the same timestamp.
 *
 * <p>Written to a scratch file beside the target and renamed onto it, so the target's name never
 * names a file that is still being written into. That is what lets every other reader of the
 * directory treat the archive's presence as the whole story: the compression job deletes the
 * recording once the archive exists, a listing that shows an archive shows a complete one, and a
 * download that resolves one is reading bytes nothing will touch again.
 */
final class Lz4Compression implements Compression {

    private static final Logger LOG = LoggerFactory.getLogger(Lz4Compression.class);

    private static final String SUFFIX = ".lz4";

    /**
     * A leading dot keeps the scratch file out of every listing — {@code _listRepositoryFiles}
     * drops hidden files — and the {@code .tmp} tail keeps it out of the one type that matches on
     * a trailing {@code ~}, async-profiler's own cache file, which is a real file of the session
     * and must not be confused with this.
     */
    private static final String SCRATCH_PREFIX = ".";
    private static final String SCRATCH_SUFFIX = ".tmp";
    private static final String SCRATCH_SEPARATOR = ".";

    @Override
    public Path target(Path source) {
        return source.resolveSibling(source.getFileName() + SUFFIX);
    }

    @Override
    public Path compress(Path source, Path target) {
        Path scratch = scratch(target);
        try {
            Lz4Compressor.compress(source, scratch);

            // Before the rename, because after it the file is the answer. An archive of no bytes
            // is a compression that failed quietly, and publishing one would let the caller
            // delete a recording it has not actually copied.
            if (Files.size(scratch) == 0) {
                throw new IllegalStateException(
                        "Compressing " + source + " produced no bytes; leaving the recording as it is");
            }
            return publish(scratch, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress file: source=" + source + " target=" + target, e);
        } finally {
            // A no-op on the way out of a successful publish, since the rename took the file
            // with it. On any failure this is what keeps the scratch file from outliving the
            // attempt that wrote it.
            removeScratch(scratch);
        }
    }

    /**
     * Renames the finished archive onto its own name in one step, which on every filesystem the
     * hub runs on is a single directory operation: a reader sees the old name or the new one and
     * never a file in between.
     *
     * <p>A filesystem that cannot promise that much still gets the archive, because the
     * alternative is refusing to compress on it at all. It is said out loud, once per file, since
     * it is the one case where a reader can still see a partial archive.
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
     * Where the archive is written before it has a name anyone reads. Beside the target rather
     * than in a temp directory, so the rename that publishes it stays within one directory and
     * therefore within one filesystem.
     *
     * <p>Unique per attempt, and that is load-bearing rather than tidy: the lock the compression
     * job takes is held by one storage instance, and a storage instance is built per call, so two
     * runs over the same session genuinely overlap. Sharing one scratch name, the second would
     * write into the file the first is about to rename, and the rename would publish a half
     * archive under a name that means "whole".
     *
     * <p>What this does not do is reclaim a scratch file left behind by a hub that was killed
     * mid-compression. It is hidden, so nothing lists it and no quota counts it, and the session
     * directory takes it along when the session goes; a sweep would have to tell a stale scratch
     * file from one an overlapping run is writing this moment, which is exactly the distinction
     * the unique name gives up on knowing.
     */
    private static Path scratch(Path target) {
        String name = SCRATCH_PREFIX + target.getFileName() + SCRATCH_SEPARATOR + UUID.randomUUID() + SCRATCH_SUFFIX;
        return target.resolveSibling(name);
    }

    private static void removeScratch(Path scratch) {
        try {
            Files.deleteIfExists(scratch);
        } catch (IOException e) {
            LOG.warn("Cannot remove the scratch file of a compression: scratch={}", scratch, e);
        }
    }
}
