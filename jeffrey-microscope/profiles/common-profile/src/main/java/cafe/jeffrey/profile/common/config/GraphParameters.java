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

package cafe.jeffrey.profile.common.config;

import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.WeightUnit;
import cafe.jeffrey.profile.common.analysis.marker.Marker;
import cafe.jeffrey.shared.common.model.StacktraceTag;
import cafe.jeffrey.shared.common.model.StacktraceType;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.ArrayList;
import java.util.List;

public record GraphParameters(
        Type eventType,
        RelativeTimeRange timeRange,
        ThreadInfo threadInfo,
        String searchPattern,
        boolean threadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        boolean parseLocations,
        List<Marker> markers,
        GraphType graphType,
        GraphComponents graphComponents,
        List<SpanInterval> spanIntervals,
        // How the sample weight is measured (bytes / duration / none). Resolved from the event type's
        // stored sample unit; drives builder + frame-processor selection for aggregated stack-sample
        // formats (pprof / OTLP). NONE for JFR (which is classified by the event-type Type instead).
        WeightUnit weightUnit,
        // Whether the profile is a flamegraph-only import (pprof / OTLP). When true the generator selects the
        // builder / frame-processor purely from weightUnit and never falls back to JFR event-type predicates —
        // so an imported count dimension whose code equals a JFR allocation/blocking code still renders plain.
        boolean flamegraphOnlyImport) {

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

    public GraphParametersBuilder toBuilder() {
        return builder()
                .withEventType(eventType)
                .withTimeRange(timeRange)
                .withThreadInfo(threadInfo)
                .withSearchPattern(searchPattern)
                .withThreadMode(threadMode)
                .withUseWeight(useWeight)
                .withExcludeNonJavaSamples(excludeNonJavaSamples)
                .withExcludeIdleSamples(excludeIdleSamples)
                .withOnlyUnsafeAllocationSamples(onlyUnsafeAllocationSamples)
                .withParseLocation(parseLocations)
                .withMarkers(markers)
                .withGraphType(graphType)
                .withGraphComponents(graphComponents)
                .withSpanIntervals(spanIntervals)
                .withWeightUnit(weightUnit)
                .withFlamegraphOnlyImport(flamegraphOnlyImport);
    }

    public static GraphParametersBuilder builder() {
        return new GraphParametersBuilder();
    }
}
