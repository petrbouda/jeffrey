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
import java.time.Instant;
import java.util.Objects;

public final class DiffConfigBuilder extends ConfigBuilder<DiffConfigBuilder> {
    Path secondaryRecording;
    Instant secondaryStart;

    public DiffConfigBuilder() {
        super(Config.Type.DIFFERENTIAL);
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
        Objects.requireNonNull(secondaryRecording, "Secondary JFR file as a source of data needs to be specified");
        Objects.requireNonNull(secondaryStart, "Start time of the profile needs to be specified");

        AbsoluteTimeRange primaryRange = resolveTimeRange(primaryStart);
        AbsoluteTimeRange secondaryRange;
        if (timeRange == null) {
            secondaryRange = AbsoluteTimeRange.UNLIMITED;
        } else {
            secondaryRange = resolveTimeRange(secondaryStart);
        }

        return new Config(
                type,
                primaryRecording,
                secondaryRecording,
                eventType,
                primaryStart,
                secondaryStart,
                primaryRange,
                secondaryRange,
                searchPattern,
                threadMode,
                collectWeight);
    }
}
