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

package pbouda.jeffrey.common.model.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public final class RepositoryFile {
    private final String id;
    private final String name;
    private final Instant createdAt;
    private final Instant modifiedAt;
    private final Instant finishedAt;
    private final Long size;
    private final SupportedRecordingFile fileType;
    private final boolean isRecordingFile;
    private final Path filePath;

    private boolean isFinishingFile;
    private RecordingStatus status;

    public RepositoryFile(
            String id,
            String name,
            Instant createdAt,
            Instant modifiedAt,
            Instant finishedAt,
            Long size,
            SupportedRecordingFile fileType,
            boolean isRecordingFile, boolean isFinishingFile,
            RecordingStatus status,
            Path filePath) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.finishedAt = finishedAt;
        this.size = size;
        this.fileType = fileType;
        this.isRecordingFile = isRecordingFile;
        this.isFinishingFile = isFinishingFile;
        this.status = status;
        this.filePath = filePath;
    }

    public void withNonFinishedStatus(RecordingStatus status) {
        this.status = status;
    }

    public void withIsFinishingFile(boolean isFinishingFile) {
        this.isFinishingFile = isFinishingFile;
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

    public Instant modifiedAt() {
        return modifiedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Long size() {
        return size;
    }

    public SupportedRecordingFile fileType() {
        return fileType;
    }

    public boolean isRecordingFile() {
        return isRecordingFile;
    }

    public boolean isFinishingFile() {
        return isFinishingFile;
    }

    public RecordingStatus status() {
        return status;
    }

    public Path filePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RepositoryFile that)) return false;
        return isRecordingFile == that.isRecordingFile
               && isFinishingFile == that.isFinishingFile
               && Objects.equals(id, that.id)
               && Objects.equals(name, that.name)
               && Objects.equals(createdAt, that.createdAt)
               && Objects.equals(modifiedAt, that.modifiedAt)
               && Objects.equals(finishedAt, that.finishedAt)
               && Objects.equals(size, that.size)
               && fileType == that.fileType
               && Objects.equals(filePath, that.filePath)
               && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, name, createdAt, modifiedAt, finishedAt,
                size, fileType, isRecordingFile, isFinishingFile, filePath, status);
    }
}
