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

package pbouda.jeffrey.common.config;

import pbouda.jeffrey.common.ConfigUtils;
import pbouda.jeffrey.common.ProfilingStartEnd;

import java.nio.file.Path;
import java.util.Objects;

public final class DiffConfigBuilder extends ConfigBuilder<DiffConfigBuilder> {
    String secondaryId;
    Path secondaryRecordingDir;
    Path secondaryRecording;
    ProfilingStartEnd secondaryStartEnd;

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

    public DiffConfigBuilder withSecondaryStartEnd(ProfilingStartEnd secondaryStartEnd) {
        this.secondaryStartEnd = secondaryStartEnd;
        return this;
    }

    @Override
    public Config build() {
        if (secondaryRecording == null && secondaryRecordingDir == null) {
            throw new IllegalArgumentException(
                    "One of the 'secondaryRecording' or 'secondaryRecordingDir' can be specified");
        }
        Objects.requireNonNull(secondaryStartEnd, "Start time of the profile needs to be specified");

        return new Config(
                type,
                primaryId,
                secondaryId,
                ConfigUtils.resolveRecordings(primaryRecording, primaryRecordingDir),
                ConfigUtils.resolveRecordings(secondaryRecording, secondaryRecordingDir),
                eventType,
                graphParameters,
                primaryStartEnd,
                secondaryStartEnd,
                resolveTimeRange(),
                threadInfo);
    }
}
