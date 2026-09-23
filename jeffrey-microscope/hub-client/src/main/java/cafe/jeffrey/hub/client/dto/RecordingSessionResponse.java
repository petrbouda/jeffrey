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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public record RecordingSessionResponse(
        String id,
        String name,
        String instanceId,
        Long createdAt,
        Long finishedAt,
        RecordingStatus status,
        Long duration,
        List<RepositoryFileResponse> files,
        boolean retained) {

    public static RecordingSessionResponse from(RecordingSession session, Clock clock) {
        Instant end = session.finishedAt() != null ? session.finishedAt() : clock.instant();
        long duration = end.toEpochMilli() - session.createdAt().toEpochMilli();

        return new RecordingSessionResponse(
                session.id(),
                session.name(),
                session.instanceId(),
                InstantUtils.toEpochMilli(session.createdAt()),
                InstantUtils.toEpochMilli(session.finishedAt()),
                session.status(),
                duration,
                session.files().stream()
                        .map(file -> RepositoryFileResponse.from(session, file))
                        .toList(),
                session.retained());
    }

    /**
     * The same session with every file's status resolved against it.
     *
     * <p>For the decoder, and only for it: the wire carries no per-file status, because whether
     * a file is still being written is a fact about the session rather than about the file. A
     * session decoded from a hub therefore arrives with that column unfilled, and it is filled
     * here, once, instead of leaving each reader of a row to work it out or to trust a value
     * nobody set. {@code RepositoryClient} calls this as the last step of decoding a session, so
     * no session with an unfilled column is ever handed out and there is nothing for a caller to
     * remember.
     */
    public RecordingSessionResponse withResolvedFileStatuses() {
        RecordingSession session = from(this);
        return new RecordingSessionResponse(
                id, name, instanceId, createdAt, finishedAt, status, duration,
                session.files().stream()
                        .map(file -> RepositoryFileResponse.from(session, file))
                        .toList(),
                retained);
    }

    public static RecordingSession from(RecordingSessionResponse response) {
        return new RecordingSession(
                response.id(),
                response.name(),
                response.instanceId(),
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()),
                response.status(),
                null,
                response.files().stream()
                        .map(RepositoryFileResponse::from)
                        .toList(),
                response.retained());
    }
}
