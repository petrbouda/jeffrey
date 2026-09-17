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
