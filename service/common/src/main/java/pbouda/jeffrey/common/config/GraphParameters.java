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

import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.model.profile.StacktraceTag;
import pbouda.jeffrey.common.model.profile.StacktraceType;
import pbouda.jeffrey.common.time.RelativeTimeRange;

import java.util.ArrayList;
import java.util.List;

public record GraphParameters(
        Type eventType,
        RelativeTimeRange timeRange,
        ThreadInfo threadInfo,
        String searchPattern,
        boolean threadMode,
        boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        boolean parseLocations,
        List<Marker> markers,
        GraphType graphType,
        GraphComponents graphComponents) {

    public List<StacktraceTag> stacktraceTags() {
        List<StacktraceTag> tags = new ArrayList<>();
        if (onlyUnsafeAllocationSamples) {
            tags.add(StacktraceTag.UNSAFE_ALLOCATION);
        }
        if (excludeIdleSamples) {
            tags.add(StacktraceTag.EXCLUDE_IDLE);
        }
        return tags;
    }

    public List<StacktraceType> stacktraceTypes() {
        List<StacktraceType> types = new ArrayList<>();
        if (excludeNonJavaSamples) {
            types.add(StacktraceType.APPLICATION);
        }
        return types;
    }

    public boolean containsMarkers() {
        return markers != null && !markers.isEmpty();
    }

    public boolean containsSearchPattern() {
        return searchPattern != null;
    }

    public static GraphParametersBuilder builder() {
        return new GraphParametersBuilder();
    }
}
