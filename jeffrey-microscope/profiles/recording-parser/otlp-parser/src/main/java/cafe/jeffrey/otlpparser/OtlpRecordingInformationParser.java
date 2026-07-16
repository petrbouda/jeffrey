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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.Profile;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import io.opentelemetry.proto.profiles.v1development.ResourceProfiles;
import io.opentelemetry.proto.profiles.v1development.Sample;
import io.opentelemetry.proto.profiles.v1development.ScopeProfiles;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

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

    @Override
    public RecordingInformation provide(Path recordingPath) {
        TimeRange timeRange = new TimeRange();

        OtlpStreamReader streamReader = new OtlpStreamReader();
        streamReader.read(recordingPath, frame -> acceptFrame(frame, timeRange));

        if (timeRange.isEmpty()) {
            throw new IllegalArgumentException(
                    "OTLP recording contains no usable timestamps: " + recordingPath);
        }

        return new RecordingInformation(
                FileSystemUtils.size(recordingPath),
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
