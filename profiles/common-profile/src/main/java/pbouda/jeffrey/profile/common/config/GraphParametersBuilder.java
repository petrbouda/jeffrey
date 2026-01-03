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

package pbouda.jeffrey.profile.common.config;

import pbouda.jeffrey.shared.common.GraphType;
import pbouda.jeffrey.shared.common.model.ThreadInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.common.analysis.marker.Marker;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.List;

public class GraphParametersBuilder {

    private Type eventType;
    private RelativeTimeRange timeRange;
    private ThreadInfo threadInfo;
    private String searchPattern;
    private boolean threadMode;
    private Boolean collectWeight;
    private boolean excludeNonJavaSamples;
    private boolean excludeIdleSamples;
    private boolean onlyUnsafeAllocationSamples;
    private boolean parseLocations;
    private List<Marker> markers;
    private GraphType graphType;
    private GraphComponents graphComponents;

    public GraphParametersBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public GraphParametersBuilder withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    public GraphParametersBuilder withThreadInfo(ThreadInfo threadInfo) {
        this.threadInfo = threadInfo;
        return this;
    }

    public GraphParametersBuilder withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return this;
    }

    public GraphParametersBuilder withThreadMode(boolean threadMode) {
        this.threadMode = threadMode;
        return this;
    }

    public GraphParametersBuilder withUseWeight(Boolean collectWeight) {
        this.collectWeight = collectWeight;
        return this;
    }

    public GraphParametersBuilder withExcludeNonJavaSamples(boolean excludeNonJavaSamples) {
        this.excludeNonJavaSamples = excludeNonJavaSamples;
        return this;
    }

    public GraphParametersBuilder withExcludeIdleSamples(boolean excludeIdleSamples) {
        this.excludeIdleSamples = excludeIdleSamples;
        return this;
    }

    public GraphParametersBuilder withOnlyUnsafeAllocationSamples(boolean onlyUnsafeAllocationSamples) {
        this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
        return this;
    }

    public GraphParametersBuilder withParseLocation(boolean parseLocations) {
        this.parseLocations = parseLocations;
        return this;
    }

    public GraphParametersBuilder withMarkers(List<Marker> markers) {
        this.markers = markers;
        return this;
    }

    public GraphParametersBuilder withGraphType(GraphType graphType) {
        this.graphType = graphType;
        return this;
    }

    public GraphParametersBuilder withGraphComponents(GraphComponents components) {
        this.graphComponents = components;
        return this;
    }

    public GraphParameters build() {
        return new GraphParameters(
                eventType,
                timeRange,
                threadInfo,
                searchPattern,
                threadMode,
                collectWeight,
                excludeNonJavaSamples,
                excludeIdleSamples,
                onlyUnsafeAllocationSamples,
                parseLocations,
                markers,
                graphType,
                graphComponents);
    }
}
