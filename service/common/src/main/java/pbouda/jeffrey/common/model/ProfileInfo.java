/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps basic information about the profile.
 *
 * @param id                    ID of the profile
 * @param projectId             ID of the project where the profile belongs to
 * @param name                  Name of the profile
 * @param originalRecordingName Original name of the recording file
 * @param createdAt             Time when the profile was created
 * @param startedAt             Resolved using ActiveRecording and recordingStart field
 *                              (the earliest one in case of multiple chunks)
 * @param endedAt               Resolved as the latest event using `event.getEndTime()`
 */
public record ProfileInfo(
        String id,
        String projectId,
        String name,
        String originalRecordingName,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt) {

    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }
}