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
 * One file of a recording session as the repository reports it.
 *
 * @param id        the file's identity within its workspace: its path relative to the workspace
 *                  with the recording extension stripped, so it survives compression
 * @param name      the file's own name, relative to the session directory
 * @param createdAt when the profiler opened the file — the timestamp in its own name for a chunk
 *                  that follows the naming convention, its filesystem creation time otherwise
 * @param size      the file's size in bytes, or {@code null} when it could not be read
 * @param fileType  what the name says the file is
 * @param status    FINISHED for every file except the chunk a live session is still writing
 * @param filePath  the absolute path, or {@code null} for a file described from the wire
 */
public record RepositoryFile(
        String id,
        String name,
        Instant createdAt,
        Long size,
        SupportedRecordingFile fileType,
        RecordingStatus status,
        Path filePath) {

    /**
     * The same file in another status. A copy rather than a write, like every other {@code withX}
     * in this package: this is a value the listing hands out, and a listing that mutated one of its
     * own entries after publishing it could be observed mid-change and cannot be reasoned about
     * from the entry alone.
     */
    public RepositoryFile withStatus(RecordingStatus newStatus) {
        return new RepositoryFile(id, name, createdAt, size, fileType, newStatus, filePath);
    }

    public boolean isRecordingFile() {
        return fileType.fileCategory() == FileCategory.RECORDING;
    }

    public boolean isArtifactFile() {
        return fileType.fileCategory() == FileCategory.ARTIFACT;
    }

    public boolean isFinished() {
        return status == RecordingStatus.FINISHED;
    }
}
