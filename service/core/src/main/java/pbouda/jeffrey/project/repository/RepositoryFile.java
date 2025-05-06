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

package pbouda.jeffrey.project.repository;

import pbouda.jeffrey.model.SupportedRecordingFile;

import java.time.Instant;

public record RepositoryFile(
        String id,
        String name,
        Instant createdAt,
        Instant modifiedAt,
        Instant finishedAt,
        Long size,
        SupportedRecordingFile fileType,
        boolean isRecordingFile,
        RecordingStatus status) {

    public RepositoryFile withNonFinishedStatus(RecordingStatus status) {
        return new RepositoryFile(id, name, createdAt, modifiedAt, null, size, fileType, isRecordingFile, status);
    }
}
