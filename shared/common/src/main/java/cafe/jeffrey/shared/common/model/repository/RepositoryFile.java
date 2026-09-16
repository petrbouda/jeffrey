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

package cafe.jeffrey.shared.common.model.repository;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One file of a recording session, as a repository reports it.
 *
 * <p>A file on its own carries no status. Whether it is still being written is a fact about its
 * session — the profiler holds one chunk open, and only while the session records — so the
 * question is asked of {@link RecordingSession}, which knows both. Carrying the answer here
 * instead meant every producer of a listing had to work it out and every consumer had to trust
 * that it had.
 *
 * @param id        the file's identity within its session: its own name with the recording
 *                  extension stripped, so it survives the hub compressing the file
 * @param name      the file's name, relative to the session directory
 * @param createdAt when the profiler opened the file — the timestamp in its own name for a chunk
 *                  that follows the naming convention, its filesystem creation time otherwise
 * @param size      the file's size in bytes, or {@code null} when it could not be read
 * @param fileType  what the name says the file is
 * @param filePath  the absolute path, or {@code null} for a file described from the wire
 */
public record RepositoryFile(
        String id,
        String name,
        Instant createdAt,
        Long size,
        ManagedFile fileType,
        Path filePath) {

    public boolean isRecordingFile() {
        return fileType.fileCategory() == FileCategory.RECORDING;
    }

    public boolean isArtifactFile() {
        return fileType.fileCategory() == FileCategory.ARTIFACT;
    }

    /**
     * Whether the file holds anything at all.
     *
     * <p>A profiler stopped before it wrote an event — a container killed, a shutdown that was
     * not graceful — leaves a zero-byte recording behind, and a recording of no bytes is not
     * something any reader can parse. The hub refuses to serve one and a download must not ask
     * for one; the question is asked here so the two cannot come to different answers.
     *
     * <p>A size that could not be read at all counts as content: that is the one case where
     * nothing here can tell, and treating it as empty would withhold a recording that may be
     * whole. Worth asking of a recording only — an empty log is an answer, and a reader wanting
     * one wants to be handed the nothing it holds.
     */
    public boolean hasContent() {
        return size == null || size > 0;
    }
}
