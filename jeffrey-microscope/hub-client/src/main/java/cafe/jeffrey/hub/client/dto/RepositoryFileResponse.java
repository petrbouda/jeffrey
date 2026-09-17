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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.hub.client.RepositoryFiles;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

public record RepositoryFileResponse(
        String id,
        String name,
        Long createdAt,
        Long size,
        ManagedFile fileType,
        RecordingStatus status,
        boolean isRecording) {

    /**
     * The file as this application's own API reports it, with the status resolved here.
     *
     * <p>A file has no status of its own and none comes over the wire: the hub says which files
     * exist, and whether one is still being written is a fact about the session, which
     * {@link RecordingSession#isOpen} answers. It is resolved at this boundary because a reader
     * of this response holds one file at a time — a table row, a checkbox — and asking it to
     * carry the session around to find out would put the rule in every such reader.
     */
    public static RepositoryFileResponse from(RecordingSession session, RepositoryFile file) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                InstantUtils.toEpochMilli(file.createdAt()),
                file.size(),
                RepositoryFiles.typeOf(file),
                session.isOpen(file) ? session.status() : RecordingStatus.FINISHED,
                file.isRecordingFile());
    }

    public static RepositoryFile from(RepositoryFileResponse response) {
        return new RepositoryFile(
                response.id(),
                response.name(),
                InstantUtils.fromEpochMilli(response.createdAt()),
                response.size(),
                response.isRecording(),
                null);
    }
}
