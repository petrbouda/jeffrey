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

package cafe.jeffrey.microscope.connector.api.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.connector.api.RepositoryFiles;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.util.OptionalLong;

/**
 * @param sequence        the file number async-profiler wrote into a recording's name
 *                        ({@link JeffreyLayout#recordingSequence}), {@code null} for a name without
 *                        one and for any other file: what a session's chunks are ordered by, so a
 *                        reader holding them orders them as the backend does
 * @param newestRecording whether it is the newest recording of its instance's newest session: the
 *                        one the hub keeps uncompressed, which may still be on its way onto the volume
 */
public record RepositoryFileResponse(
        String id,
        String name,
        Long createdAt,
        Long sequence,
        Long size,
        ManagedFile fileType,
        boolean newestRecording,
        boolean isRecording) {

    /**
     * The file as this application's own API reports it.
     *
     * <p>A file has no status of its own: whether it may still be on its way is a fact about the
     * session ({@link RecordingSession#openRecording()}), resolved once per session by the caller
     * and handed to each file here, because a reader of this response holds one file at a time — a
     * table row, a checkbox — and asking it to carry the session around would put the rule in
     * every such reader.
     */
    public static RepositoryFileResponse from(RepositoryFile file, boolean newestRecording) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                InstantUtils.toEpochMilli(file.createdAt()),
                sequenceOf(file),
                file.size(),
                RepositoryFiles.typeOf(file),
                newestRecording,
                file.isRecordingFile());
    }

    private static Long sequenceOf(RepositoryFile file) {
        if (!file.isRecordingFile()) {
            return null;
        }
        OptionalLong sequence = JeffreyLayout.recordingSequence(file.id());
        return sequence.isPresent() ? sequence.getAsLong() : null;
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
