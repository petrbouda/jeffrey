/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps basic information about the profile.
 *
 * @param id                  ID of the profile
 * @param projectId           ID of the project where the profile belongs to (null for Quick Analysis)
 * @param workspaceId         ID of the workspace where the profile belongs to (null for Quick Analysis)
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
        String workspaceId,
        String name,
        RecordingEventSource eventSource,
        Instant profilingStartedAt,
        Instant profilingFinishedAt,
        Instant createdAt,
        boolean enabled,
        boolean modified,
        String recordingId) {

    public Duration duration() {
        return Duration.between(profilingStartedAt, profilingFinishedAt);
    }

    public  ProfilingStartEnd profilingStartEnd() {
        return new ProfilingStartEnd(profilingStartedAt, profilingFinishedAt);
    }
}
