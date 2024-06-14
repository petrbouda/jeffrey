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

package pbouda.jeffrey.generator.heatmap;

import pbouda.jeffrey.common.Type;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class HeatmapConfigBuilder {
    private Path recording;
    private Type eventType;
    private Instant profilingStart;
    private Duration heatmapStart = Duration.ZERO;
    private Duration duration;
    private boolean collectWeight;

    public HeatmapConfigBuilder withRecording(Path recording) {
        this.recording = recording;
        return this;
    }

    public HeatmapConfigBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public HeatmapConfigBuilder withProfilingStart(Instant profilingStart) {
        this.profilingStart = profilingStart;
        return this;
    }

    public HeatmapConfigBuilder withHeatmapStart(Duration heatmapStart) {
        this.heatmapStart = heatmapStart;
        return this;
    }

    public HeatmapConfigBuilder withDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public HeatmapConfigBuilder withCollectWeight(boolean collectWeight) {
        this.collectWeight = collectWeight;
        return this;
    }

    public HeatmapConfig build() {
        Objects.requireNonNull(recording, "JFR file as a source of data needs to be specified");
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");
        Objects.requireNonNull(profilingStart, "Start time of the profile needs to be specified");
        return new HeatmapConfig(recording, eventType, profilingStart, heatmapStart, duration, collectWeight);
    }
}
