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

package pbouda.jeffrey.shared.common.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record Recording(
        String id,
        String recordingName,
        String projectId,
        String groupId,
        RecordingEventSource eventSource,
        Instant createdAt,
        Instant recordingStartedAt,
        Instant recordingFinishedAt,
        boolean hasProfile,
        String profileId,
        String profileName,
        List<RecordingFile> files) {

    public Duration recordingDuration() {
        if (recordingStartedAt == null || recordingFinishedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(recordingStartedAt, recordingFinishedAt);
    }

    public Recording withFiles(List<RecordingFile> files) {
        return new Recording(
                id, recordingName, projectId, groupId, eventSource, createdAt,
                recordingStartedAt, recordingFinishedAt, hasProfile, profileId, profileName,
                List.copyOf(files));
    }
}
