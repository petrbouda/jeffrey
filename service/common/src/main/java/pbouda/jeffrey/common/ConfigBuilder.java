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
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class ConfigBuilder<T extends ConfigBuilder<?>> {
    Config.Type type;
    Path primaryRecordingDir;
    Path primaryRecording;
    Type eventType;
    Instant primaryStart;
    TimeRange timeRange;
    String searchPattern;
    boolean threadMode;
    boolean collectWeight;

    public ConfigBuilder() {
        this(Config.Type.PRIMARY);
    }

    public ConfigBuilder(Config.Type type) {
        this.type = type;
    }

    public T withPrimaryRecordingDir(Path recordingDir) {
        this.primaryRecordingDir = recordingDir;
        return (T) this;
    }

    public T withPrimaryRecording(Path recording) {
        this.primaryRecording = recording;
        return (T) this;
    }

    public T withEventType(Type eventType) {
        this.eventType = eventType;
        return (T) this;
    }

    public T withPrimaryStart(Instant profilingStart) {
        this.primaryStart = profilingStart;
        return (T) this;
    }

    public T withTimeRange(TimeRange timeRange) {
        this.timeRange = timeRange;
        return (T) this;
    }

    public T withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return (T) this;
    }

    public T withThreadMode(boolean threadMode) {
        this.threadMode = threadMode;
        return (T) this;
    }

    public T withCollectWeight(boolean collectWeight) {
        this.collectWeight = collectWeight;
        return (T) this;
    }

    protected AbsoluteTimeRange resolveTimeRange(Instant start) {
        return switch (timeRange) {
            case AbsoluteTimeRange tr -> tr;
            case RelativeTimeRange tr when start != null -> tr.toAbsoluteTimeRange(start);
            case RelativeTimeRange __ -> throw new IllegalArgumentException("`relativeTimeRange` only with `primaryStart`");
            case null -> AbsoluteTimeRange.UNLIMITED;
        };
    }

    public Config build() {
        return new Config(
                type,
                ConfigUtils.resolveRecordings(primaryRecording, primaryRecordingDir),
                eventType,
                primaryStart,
                resolveTimeRange(primaryStart),
                searchPattern,
                threadMode,
                collectWeight);
    }
}
