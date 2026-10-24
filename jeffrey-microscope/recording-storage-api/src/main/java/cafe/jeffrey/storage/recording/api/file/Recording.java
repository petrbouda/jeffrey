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

/**
 * One initializable item: a JFR recording (all its chunk files), a heap dump, a pprof or OTLP profile,
 * a perf-counters snapshot. It becomes at most one profile.
 *
 * @param bundleId the bundle this recording belongs to, or {@code null} for a standalone upload or
 *                 import. A bundle groups the items a multi-file download brought in together.
 */
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
        List<RecordingFile> files,
        String bundleId) {

    /** A standalone recording: one that belongs to no bundle. */
    public Recording(
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

        this(id, recordingName, groupId, eventSource, createdAt, recordingStartedAt, recordingFinishedAt,
                hasProfile, profileId, profileName, files, null);
    }

    public boolean inBundle() {
        return bundleId != null;
    }

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
                List.copyOf(files), bundleId);
    }
}
