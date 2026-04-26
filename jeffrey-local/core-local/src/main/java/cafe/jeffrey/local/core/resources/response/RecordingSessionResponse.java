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

package cafe.jeffrey.local.core.resources.response;

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
        List<RepositoryFileResponse> files) {

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
                        .map(RepositoryFileResponse::from)
                        .toList());
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
                null,
                response.files().stream()
                        .map(RepositoryFileResponse::from)
                        .toList());
    }
}
