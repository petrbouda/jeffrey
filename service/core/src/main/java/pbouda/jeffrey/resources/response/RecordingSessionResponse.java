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

package pbouda.jeffrey.resources.response;

import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;

import java.time.Instant;
import java.util.List;

public record RecordingSessionResponse(
        String id,
        String originId,
        Instant createdAt,
        RecordingStatus status,
        String profilerSettings,
        List<RepositoryFileResponse> files) {

    public static RecordingSessionResponse from(RecordingSession session) {
        return new RecordingSessionResponse(
                session.id(),
                session.originId(),
                session.createdAt(),
                session.status(),
                session.profilerSettings(),
                session.files().stream()
                        .map(RepositoryFileResponse::from)
                        .toList());
    }

    public static RecordingSession from(RecordingSessionResponse response) {
        return new RecordingSession(
                response.id(),
                response.originId(),
                response.createdAt(),
                response.status(),
                null,
                response.profilerSettings(),
                response.files().stream()
                        .map(RepositoryFileResponse::from)
                        .toList());
    }
}
