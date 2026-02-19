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

package pbouda.jeffrey.platform.resources.response;

import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

public record RepositoryFileResponse(
        String id,
        String name,
        Long createdAt,
        Long size,
        SupportedRecordingFile fileType,
        boolean isRecordingFile,
        RecordingStatus status) {

    public static RepositoryFileResponse from(RepositoryFile file) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                InstantUtils.toEpochMilli(file.createdAt()),
                file.size(),
                file.fileType(),
                file.isRecordingFile(),
                file.status());
    }

    public static RepositoryFile from(RepositoryFileResponse response) {
        return new RepositoryFile(
                response.id(),
                response.name(),
                InstantUtils.fromEpochMilli(response.createdAt()),
                response.size(),
                response.fileType(),
                response.isRecordingFile(),
                response.status(),
                null);
    }
}
