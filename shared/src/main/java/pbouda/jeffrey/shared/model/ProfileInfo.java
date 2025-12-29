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

package pbouda.jeffrey.shared.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps basic information about the profile.
 *
 * @param id                  ID of the profile
 * @param projectId           ID of the project where the profile belongs to
 * @param name                Name of the profile
 * @param createdAt           Time when the profile was created
 * @param profilingStartedAt  Resolved using ActiveRecording and recordingStart field
 *                            (the earliest one in case of multiple chunks)
 * @param profilingFinishedAt Resolved as the latest event using `event.getEndTime()`
 * @param enabled             Profile is enabled and ready to be used by the system
 */
public record ProfileInfo(
        String id,
        String projectId,
        String name,
        RecordingEventSource eventSource,
        Instant profilingStartedAt,
        Instant profilingFinishedAt,
        Instant createdAt,
        boolean enabled) {

    public Duration duration() {
        return Duration.between(profilingStartedAt, profilingFinishedAt);
    }

    public ProfilingStartEnd profilingStartEnd() {
        return new ProfilingStartEnd(profilingStartedAt, profilingFinishedAt);
    }
}
