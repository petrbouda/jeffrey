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

package cafe.jeffrey.volumescheduler.compression;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.RecordingSessionFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Compresses a session's closed JFR recordings to LZ4 in place, {@code profile-1.jfr} →
 * {@code profile-1.jfr.lz4}, and deletes each original once its archive is whole. Only a JFR is
 * compressed: its compressed name keeps its type, id and timestamp, where {@code app.pprof.lz4}
 * would not, and an archive is already one. The newest recording of an instance's newest session
 * is left alone ({@link RecordingSession#trimmableRecordings()}): the profiler may still be writing
 * it, and compressing it would compress a prefix and then delete the file being written into.
 *
 * <p>An archive is written to a hidden scratch file beside it and renamed onto its name, so an
 * archive under its own name is always complete. That makes "the archive already exists" safe to
 * trust, including for two overlapping runs over one session — as long as the archive is this
 * recording's ({@link #collidesWith}). Reached only from the compression job: never compress on a
 * read path.</p>
 */
public final class RecordingCompressor {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingCompressor.class);

    private static final String ARCHIVE_SUFFIX = ".lz4";

    /**
     * @return how many of the session's recordings this call replaced with their archive — written
     *         now, or found whole where an interrupted run left it
     */
    public int compress(RecordingSession session) {
        int compressed = 0;
        for (RecordingSessionFile recording : session.trimmableRecordings()) {
            if (compressOne(session.id(), recording)) {
                compressed++;
            }
        }
        return compressed;
    }

    /** @return whether a newly written or found archive replaced the recording */
    private static boolean compressOne(String sessionId, RecordingSessionFile recording) {
        boolean compressible = RecordingFormat.of(recording.name())
                .map(format -> !format.isArchive())
                .orElse(false);
        if (!compressible) {
            LOG.debug("Leaving an already compressed recording alone: session_id={} file={}",
                    sessionId, recording.name());
            return false;
        }

        Path source = recording.path();
        Path archive = source.resolveSibling(source.getFileName() + ARCHIVE_SUFFIX);

        // Already compressed, here or by an overlapping run; the archive is whole by construction
        if (Files.exists(archive)) {
            if (collidesWith(source, archive)) {
                LOG.warn("Kept a recording whose name an older recording's archive already holds: "
                        + "session_id={} file={} archive={}", sessionId, source, archive);
                return false;
            }
            FileSystemUtils.removeFile(source);
            return true;
        }

        // An empty recording is left when the profiler stops before writing events, e.g. after a
        // non-graceful shutdown
        if (FileSystemUtils.size(source) == 0) {
            LOG.debug("Skipping an empty recording: session_id={} file={}", sessionId, source);
            return false;
        }

        writeArchive(source, archive);
        FileSystemUtils.removeFile(source);
        return true;
    }

    /**
     * Writes the archive whole or not at all: to a scratch file, then renamed onto its name, so its
     * existence proves the bytes are complete and the source may be deleted. The source is left
     * alone here.
     *
     * @throws RuntimeException when the archive could not be written, in which case it is left as
     *                          it was and the source is untouched
     */
    private static void writeArchive(Path source, Path archive) {
        Path scratch = archive.resolveSibling(VolumePath.scratchName(archive.getFileName().toString()));
        try {
            Lz4Compressor.compress(source, scratch);

            // An empty archive is a silent failure; publishing it would let the recording be deleted
            if (Files.size(scratch) == 0) {
                throw new IllegalStateException(
                        "Compressing " + source + " produced no bytes; leaving the recording as it is");
            }
            publish(scratch, archive);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress file: source=" + source + " target=" + archive, e);
        } finally {
            // A no-op after a successful rename; on failure it removes the partial scratch file
            removeScratch(scratch);
        }
    }

    /**
     * Renames the finished archive onto its name atomically; a filesystem without atomic moves
     * still gets the archive, with a warning since a reader may then see it partial.
     */
    private static void publish(Path scratch, Path archive) throws IOException {
        try {
            Files.move(scratch, archive, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.warn("Filesystem cannot rename atomically, so a reader may briefly see a partial archive: "
                    + "archive={}", archive);
            Files.move(scratch, archive, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void removeScratch(Path scratch) {
        try {
            Files.deleteIfExists(scratch);
        } catch (IOException e) {
            LOG.warn("Cannot remove the scratch file of a compression: scratch={}", scratch, e);
        }
    }

    /**
     * Whether an archive beside a recording is another recording's that happened to have the same
     * name, rather than this one's, compressed by a run that stopped before deleting its source.
     * An archive is written after its source is complete, so one older than the source holds an
     * earlier chunk: two names collided (a template without the file number, through a
     * daylight-saving switch), and deleting the source would delete the newer chunk. Times that
     * cannot be read count as a collision: keeping a file costs only its space.
     */
    private static boolean collidesWith(Path source, Path archive) {
        try {
            return Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(archive)) > 0;
        } catch (IOException e) {
            LOG.warn("Cannot compare a recording with the archive of the same name, keeping both: source={} archive={}",
                    source, archive, e);
            return true;
        }
    }
}
