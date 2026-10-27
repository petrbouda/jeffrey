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

package cafe.jeffrey.microscope.remote.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.remote.client.RepositoryFiles;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

/**
 * @param newestRecording whether it is the newest recording of its instance's newest session: the
 *                        one the hub keeps uncompressed, which may still be on its way onto the volume
 */
public record RepositoryFileResponse(
        String id,
        String name,
        Long createdAt,
        Long size,
        ManagedFile fileType,
        boolean newestRecording,
        boolean isRecording) {

    /**
     * The file as this application's own API reports it, with its flag resolved here.
     *
     * <p>A file has no status of its own and none comes with the listing: the volume says which
     * files exist, and whether one may still be on its way is a fact about the session, which
     * {@link RecordingSession#isOpen} answers. It is resolved at this boundary because a reader of
     * this response holds one file at a time — a table row, a checkbox — and asking it to carry
     * the session around to find out would put the rule in every such reader.
     */
    public static RepositoryFileResponse from(RecordingSession session, RepositoryFile file) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                InstantUtils.toEpochMilli(file.createdAt()),
                file.size(),
                RepositoryFiles.typeOf(file),
                session.isOpen(file),
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
