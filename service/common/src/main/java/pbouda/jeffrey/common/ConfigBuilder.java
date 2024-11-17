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

@SuppressWarnings("unchecked")
public class ConfigBuilder<T extends ConfigBuilder<?>> {
    Config.Type type;
    String primaryId;
    Path primaryRecordingDir;
    Path primaryRecording;
    Type eventType;
    ProfilingStartEnd primaryStartEnd;
    TimeRange timeRange;
    String searchPattern;
    boolean threadMode;
    // Useful only for Timeseries graph, not for Flamegraph (flamegraphs always generates both and let UI choosing one)
    boolean collectWeight;
    boolean excludeNonJavaSamples;
    boolean excludeIdleSamples;
    // Parse line and bytecode numbers from JFR events (Automatically enabled, disabling means performance boost)
    boolean parseLocations = true;

    public ConfigBuilder() {
        this(Config.Type.PRIMARY);
    }

    public ConfigBuilder(Config.Type type) {
        this.type = type;
    }

    public T withPrimaryId(String primaryId) {
        this.primaryId = primaryId;
        return (T) this;
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

    public T withPrimaryStartEnd(ProfilingStartEnd primaryStartEnd) {
        this.primaryStartEnd = primaryStartEnd;
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

    public T withExcludeNonJavaSamples(boolean excludeNonJavaSamples) {
        this.excludeNonJavaSamples = excludeNonJavaSamples;
        return (T) this;
    }

    public T withExcludeIdleSamples(boolean excludeIdleSamples) {
        this.excludeIdleSamples = excludeIdleSamples;
        return (T) this;
    }

    public T withParseLocations(boolean parseLocations) {
        this.parseLocations = parseLocations;
        return (T) this;
    }

    protected AbsoluteTimeRange resolveTimeRange(Instant start) {
        return switch (timeRange) {
            case AbsoluteTimeRange tr -> tr;
            case RelativeTimeRange tr when start != null -> tr.toAbsoluteTimeRange(start);
            case RelativeTimeRange __ ->
                    throw new IllegalArgumentException("`relativeTimeRange` only with `primaryStart`");
            case null -> AbsoluteTimeRange.UNLIMITED;
        };
    }

    public Config build() {
        return new Config(
                type,
                primaryId,
                ConfigUtils.resolveRecordings(primaryRecording, primaryRecordingDir),
                eventType,
                primaryStartEnd,
                resolveTimeRange(primaryStartEnd.start()),
                searchPattern,
                threadMode,
                collectWeight,
                excludeNonJavaSamples,
                excludeIdleSamples,
                parseLocations);
    }
}
