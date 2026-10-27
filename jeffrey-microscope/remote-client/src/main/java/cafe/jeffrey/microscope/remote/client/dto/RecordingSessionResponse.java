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
import cafe.jeffrey.microscope.model.repository.SessionSize;

import java.util.List;

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
                session.files().stream()
                        .map(file -> RepositoryFileResponse.from(session, file))
                        .toList(),
                session.failed(),
                session.fileCount(),
                session.totalSizeBytes());
    }

    private static Long duration(Long createdAt, Long lastDataAt) {
        return createdAt != null && lastDataAt != null ? lastDataAt - createdAt : null;
    }

    /**
     * The same session with every file's flags resolved against it.
     *
     * <p>For the decoder, and only for it: the wire carries no per-file "newest recording" flag,
     * because whether a file may still be on its way is a fact about the session rather than about
     * the file. A session read off the remote therefore arrives with that column unfilled, and it
     * is filled here, once, instead of leaving each reader of a row to work it out or to trust a
     * value nobody set. The volume clients call this as the last step of decoding a session, so no
     * session with an unfilled column is ever handed out and there is nothing for a caller to
     * remember.
     */
    public RecordingSessionResponse withResolvedFiles() {
        RecordingSession session = toSession();
        return new RecordingSessionResponse(
                id, name, instanceId, createdAt, lastDataAt, newest, duration,
                session.files().stream()
                        .map(file -> RepositoryFileResponse.from(session, file))
                        .toList(),
                failed,
                fileCount,
                sizeBytes);
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
