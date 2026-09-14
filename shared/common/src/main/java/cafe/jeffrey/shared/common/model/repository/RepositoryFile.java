/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import java.util.Objects;

public final class RepositoryFile {
    private final String id;
    private final String name;
    private final Instant createdAt;
    private final Long size;
    private final SupportedFile fileType;
    private final Path filePath;
    private RecordingStatus status;

    public RepositoryFile(
            String id,
            String name,
            Instant createdAt,
            Long size,
            SupportedFile fileType,
            RecordingStatus status,
            Path filePath) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.size = size;
        this.fileType = fileType;
        this.status = status;
        this.filePath = filePath;
    }

    public void withNonFinishedStatus(RecordingStatus status) {
        this.status = status;
    }


    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long size() {
        return size;
    }

    public SupportedFile fileType() {
        return fileType;
    }

    /**
     * A chunk of the session's recording — see {@link SupportedFile#isRecordingChunk()}.
     */
    public boolean isRecordingChunk() {
        return fileType.isRecordingChunk();
    }

    /**
     * A file the hub never serves — see {@link SupportedFile#isTransient()}.
     */
    public boolean isTransient() {
        return fileType.isTransient();
    }

    /**
     * Whether the hub serves this file: it is finished and not transient. The one rule every
     * download, fetch and merge applies; a file's type decides nothing else about access.
     */
    public boolean isDownloadable() {
        return isFinished() && !isTransient();
    }

    public boolean isFinished() {
        return status == RecordingStatus.FINISHED;
    }

    public RecordingStatus status() {
        return status;
    }

    public Path filePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RepositoryFile that)) {
            return false;
        }
        return Objects.equals(id, that.id)
               && Objects.equals(name, that.name)
               && Objects.equals(createdAt, that.createdAt)
               && Objects.equals(size, that.size)
               && fileType == that.fileType
               && Objects.equals(filePath, that.filePath)
               && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, name, createdAt, size, fileType, filePath, status);
    }
}
