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
import cafe.jeffrey.microscope.model.repository.SessionSize;

import java.util.List;
import java.util.Optional;

/**
 * A session as this application's API reports it.
 *
 * @param lastDataAt epoch millis when its recorded data ends; {@code null} while it has no recording
 * @param newest     whether it is its instance's newest session, which may still grow
 * @param duration   from its start to the end of its data; {@code null} while it has no recording
 */
public record RecordingSessionResponse(
        String id,
        String name,
        String instanceId,
        Long createdAt,
        Long lastDataAt,
        boolean newest,
        Long duration,
        List<RepositoryFileResponse> files,
        boolean failed,
        int fileCount,
        long sizeBytes) {

    public static RecordingSessionResponse from(RecordingSession session) {
        Long createdAt = InstantUtils.toEpochMilli(session.createdAt());
        Long lastDataAt = InstantUtils.toEpochMilli(session.lastDataAt());
        return new RecordingSessionResponse(
                session.id(),
                session.name(),
                session.instanceId(),
                createdAt,
                lastDataAt,
                session.newest(),
                duration(createdAt, lastDataAt),
                files(session),
                session.failed(),
                session.fileCount(),
                session.totalSizeBytes());
    }

    /** Its files, each flagged against the session's newest recording, worked out once. */
    private static List<RepositoryFileResponse> files(RecordingSession session) {
        Optional<RepositoryFile> newestRecording = session.openRecording();
        return session.files().stream()
                .map(file -> RepositoryFileResponse.from(file, newestRecording.filter(file::equals).isPresent()))
                .toList();
    }

    private static Long duration(Long createdAt, Long lastDataAt) {
        return createdAt != null && lastDataAt != null ? lastDataAt - createdAt : null;
    }

    /** The session in Microscope's own model, its size as the remote reported it. */
    public RecordingSession toSession() {
        return new RecordingSession(
                id,
                name,
                instanceId,
                InstantUtils.fromEpochMilli(createdAt),
                InstantUtils.fromEpochMilli(lastDataAt),
                newest,
                null,
                files.stream()
                        .map(RepositoryFileResponse::from)
                        .toList(),
                failed,
                new SessionSize(fileCount, sizeBytes));
    }
}
