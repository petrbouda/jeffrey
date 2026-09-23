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

package cafe.jeffrey.storage.recording.api.file;

import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record Recording(
        String id,
        String recordingName,
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
                id, recordingName, groupId, eventSource, createdAt,
                recordingStartedAt, recordingFinishedAt, hasProfile, profileId, profileName,
                List.copyOf(files));
    }
}
