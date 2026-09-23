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

package cafe.jeffrey.microscope.core.web.dto.response;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;

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
