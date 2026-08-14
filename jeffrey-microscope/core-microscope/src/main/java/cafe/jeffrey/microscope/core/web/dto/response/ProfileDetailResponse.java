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

package cafe.jeffrey.microscope.core.web.dto.response;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

/**
 * Wire shape of a single profile. Mirrors {@link ProfileInfo} but carries every timestamp as epoch
 * millis, so the frontend never has to parse a date string to learn when a recording started.
 *
 * @param profilingStartedAt  epoch millis, null when the recording never reported its bounds
 * @param profilingFinishedAt epoch millis, null when the recording never reported its bounds
 * @param createdAt           epoch millis
 */
public record ProfileDetailResponse(
        String id,
        String projectId,
        String workspaceId,
        String name,
        RecordingEventSource eventSource,
        Long profilingStartedAt,
        Long profilingFinishedAt,
        Long createdAt,
        boolean enabled,
        boolean modified,
        String recordingId) {

    public static ProfileDetailResponse from(ProfileInfo profileInfo) {
        return new ProfileDetailResponse(
                profileInfo.id(),
                profileInfo.projectId(),
                profileInfo.workspaceId(),
                profileInfo.name(),
                profileInfo.eventSource(),
                InstantUtils.toEpochMilli(profileInfo.profilingStartedAt()),
                InstantUtils.toEpochMilli(profileInfo.profilingFinishedAt()),
                InstantUtils.toEpochMilli(profileInfo.createdAt()),
                profileInfo.enabled(),
                profileInfo.modified(),
                profileInfo.recordingId());
    }
}
