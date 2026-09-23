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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.Profile;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import io.opentelemetry.proto.profiles.v1development.ResourceProfiles;
import io.opentelemetry.proto.profiles.v1development.Sample;
import io.opentelemetry.proto.profiles.v1development.ScopeProfiles;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Derives the recording metadata (event source + profiling start/end) of an OTLP recording by
 * scanning the profile collection times and sample timestamps of every frame. The resulting start
 * is the zero point of the relative event timeline, so the computation mirrors the timestamps the
 * {@link OtlpProfileReader} later assigns to events.
 */
public class OtlpRecordingInformationParser implements RecordingInformationParser {

    private static class TimeRange {
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos = Long.MIN_VALUE;

        private void accept(long nanos) {
            if (nanos <= 0) {
                return;
            }
            if (nanos < minNanos) {
                minNanos = nanos;
            }
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        private boolean isEmpty() {
            return minNanos == Long.MAX_VALUE;
        }
    }

    /**
     * One {@link TimeRange} over every file, so several rotated files report the span they cover
     * together. The range already folds frame by frame, and a file is just more frames.
     */
    @Override
    public RecordingInformation provide(RecordingSources sources) {
        TimeRange timeRange = new TimeRange();
        long sizeInBytes = 0;

        OtlpStreamReader streamReader = new OtlpStreamReader();
        for (Path recordingPath : sources.files()) {
            streamReader.read(recordingPath, frame -> acceptFrame(frame, timeRange));
            sizeInBytes += FileSystemUtils.size(recordingPath);
        }

        if (timeRange.isEmpty()) {
            throw new IllegalArgumentException(
                    "OTLP recording contains no usable timestamps: " + sources.files());
        }

        return new RecordingInformation(
                sizeInBytes,
                RecordingEventSource.OPEN_TELEMETRY,
                Instant.ofEpochSecond(0, timeRange.minNanos),
                Instant.ofEpochSecond(0, timeRange.maxNanos));
    }

    private static void acceptFrame(ProfilesData frame, TimeRange timeRange) {
        for (ResourceProfiles resourceProfiles : frame.getResourceProfilesList()) {
            for (ScopeProfiles scopeProfiles : resourceProfiles.getScopeProfilesList()) {
                for (Profile profile : scopeProfiles.getProfilesList()) {
                    acceptProfile(profile, timeRange);
                }
            }
        }
    }

    private static void acceptProfile(Profile profile, TimeRange timeRange) {
        timeRange.accept(profile.getTimeUnixNano());
        if (profile.getTimeUnixNano() > 0 && profile.getDurationNano() > 0) {
            timeRange.accept(profile.getTimeUnixNano() + profile.getDurationNano());
        }
        for (Sample sample : profile.getSamplesList()) {
            for (Long timestamp : sample.getTimestampsUnixNanoList()) {
                timeRange.accept(timestamp);
            }
        }
    }
}
