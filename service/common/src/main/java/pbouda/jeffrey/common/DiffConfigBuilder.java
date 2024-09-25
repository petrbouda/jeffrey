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

package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class DiffConfigBuilder extends ConfigBuilder<DiffConfigBuilder> {
    String secondaryId;
    Path secondaryRecordingDir;
    Path secondaryRecording;
    Instant secondaryStart;

    public DiffConfigBuilder() {
        super(Config.Type.DIFFERENTIAL);
    }

    public DiffConfigBuilder withSecondaryId(String secondaryId) {
        this.secondaryId = secondaryId;
        return this;
    }

    public DiffConfigBuilder withSecondaryRecordingDir(Path recordingDir) {
        this.secondaryRecordingDir = recordingDir;
        return this;
    }

    public DiffConfigBuilder withSecondaryRecording(Path recording) {
        this.secondaryRecording = recording;
        return this;
    }

    public DiffConfigBuilder withSecondaryStart(Instant profilingStart) {
        this.secondaryStart = profilingStart;
        return this;
    }

    @Override
    public Config build() {
        if (secondaryRecording == null && secondaryRecordingDir == null) {
            throw new IllegalArgumentException(
                    "One of the 'secondaryRecording' or 'secondaryRecordingDir' can be specified");
        }
        Objects.requireNonNull(secondaryStart, "Start time of the profile needs to be specified");

        AbsoluteTimeRange primaryRange = resolveTimeRange(primaryStart);
        AbsoluteTimeRange secondaryRange = timeRange == null
                ? AbsoluteTimeRange.UNLIMITED
                : resolveAndShiftTimeRange(timeRange, secondaryStart);

        return new Config(
                type,
                primaryId,
                secondaryId,
                ConfigUtils.resolveRecordings(primaryRecording, primaryRecordingDir),
                ConfigUtils.resolveRecordings(secondaryRecording, secondaryRecordingDir),
                eventType,
                primaryStart,
                secondaryStart,
                primaryRange,
                secondaryRange,
                searchPattern,
                threadMode,
                collectWeight,
                parseLocations);
    }

    private AbsoluteTimeRange resolveAndShiftTimeRange(TimeRange timeRange, Instant start) {
        Duration timeShift = Duration.between(primaryStart, secondaryStart);
        if (timeRange instanceof RelativeTimeRange relativeTimeRange) {
            return relativeTimeRange.toAbsoluteTimeRange(start);
        } else if (timeRange instanceof AbsoluteTimeRange absoluteTimeRange) {
            return shift(timeShift, absoluteTimeRange);
        } else {
            return AbsoluteTimeRange.UNLIMITED;
        }
    }

    private AbsoluteTimeRange shift(Duration timeShift, AbsoluteTimeRange tr) {
        if (timeShift.isPositive()) {
            return tr.shiftBack(timeShift);
        } else {
            return tr.shiftForward(timeShift);
        }
    }
}
