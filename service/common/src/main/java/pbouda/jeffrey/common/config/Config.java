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

import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;

public record Config(
        Type type,
        String primaryId,
        String secondaryId,
        pbouda.jeffrey.common.Type eventType,
        // Properties for the predefined flamegraphs
        GraphParameters graphParameters,
        ProfilingStartEnd primaryStartEnd,
        ProfilingStartEnd secondaryStartEnd,
        RelativeTimeRange timeRange,
        // To include records only for a specific thread
        ThreadInfo threadInfo) {

    public enum Type {
        PRIMARY, DIFFERENTIAL
    }

    public Config(
            Type type,
            String primaryId,
            pbouda.jeffrey.common.Type eventType,
            GraphParameters graphParameters,
            ProfilingStartEnd primaryStartEnd,
            RelativeTimeRange timeRange,
            ThreadInfo threadInfo) {

        this(type, primaryId, null, eventType, graphParameters,
                primaryStartEnd, null, timeRange, threadInfo);
    }

    public static ConfigBuilder<?> primaryBuilder() {
        return new ConfigBuilder<>();
    }

    public static DiffConfigBuilder differentialBuilder() {
        return new DiffConfigBuilder();
    }

    @Override
    public RelativeTimeRange timeRange() {
        if (timeRange != null) {
            return timeRange;
        } else if (primaryStartEnd != null) {
            return new RelativeTimeRange(primaryStartEnd);
        } else {
            return null;
        }
    }

    public Config copyWithType(pbouda.jeffrey.common.Type eventType) {
        return new Config(
                type, primaryId, secondaryId, eventType,
                graphParameters, primaryStartEnd, secondaryStartEnd, timeRange, threadInfo);
    }
}
