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

package pbouda.jeffrey.generator.subsecond;

import pbouda.jeffrey.common.ConfigUtils;
import pbouda.jeffrey.common.Type;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SubSecondConfigBuilder {
    private Path recording;
    private Path recordingDir;
    private Type eventType;
    private Instant profilingStart;
    private Duration generatingStart = Duration.ZERO;
    private Duration duration;
    private boolean collectWeight;

    public SubSecondConfigBuilder withRecording(Path recording) {
        this.recording = recording;
        return this;
    }


    public SubSecondConfigBuilder withRecordingDir(Path recordingDir) {
        this.recordingDir = recordingDir;
        return this;
    }

    public SubSecondConfigBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public SubSecondConfigBuilder withProfilingStart(Instant profilingStart) {
        this.profilingStart = profilingStart;
        return this;
    }

    public SubSecondConfigBuilder withGeneratingStart(Duration generatingStart) {
        this.generatingStart = generatingStart;
        return this;
    }

    public SubSecondConfigBuilder withDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public SubSecondConfigBuilder withCollectWeight(boolean collectWeight) {
        this.collectWeight = collectWeight;
        return this;
    }

    public SubSecondConfig build() {
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");
        Objects.requireNonNull(profilingStart, "Start time of the profile needs to be specified");

        return new SubSecondConfig(
                ConfigUtils.resolveRecordings(recording, recordingDir),
                eventType,
                profilingStart,
                generatingStart,
                duration,
                collectWeight);
    }
}
