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

import pbouda.jeffrey.common.*;

import java.nio.file.Path;
import java.time.Instant;

@SuppressWarnings("unchecked")
public class ConfigBuilder<T extends ConfigBuilder<?>> {
    Config.Type type;
    String primaryId;
    Path primaryRecordingDir;
    Path primaryRecording;
    Type eventType;
    GraphParameters graphParameters;
    ProfilingStartEnd primaryStartEnd;
    TimeRange timeRange;
    String searchPattern;
    // To include records only for a specific thread
    ThreadInfo threadInfo;

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

    public T withGraphParameters(GraphParameters graphParameters) {
        this.graphParameters = graphParameters;
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

    public T withThreadInfo(ThreadInfo threadInfo) {
        this.threadInfo = threadInfo;
        return (T) this;
    }

    protected AbsoluteTimeRange resolveTimeRange() {
        if (primaryStartEnd == null) {
            return AbsoluteTimeRange.UNLIMITED;
        }

        Instant start = primaryStartEnd.start();
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
                graphParameters == null ? GraphParameters.builder().build() : graphParameters,
                primaryStartEnd,
                resolveTimeRange(),
                threadInfo);
    }
}
