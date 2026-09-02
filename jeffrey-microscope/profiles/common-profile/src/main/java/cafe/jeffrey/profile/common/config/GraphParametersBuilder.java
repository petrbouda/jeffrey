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
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.WeightUnit;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.List;

public class GraphParametersBuilder {

    private Type eventType;
    private RelativeTimeRange timeRange;
    private List<ThreadInfo> threads = List.of();
    private String searchPattern;
    private boolean threadMode;
    private Boolean collectWeight;
    private boolean excludeNonJavaSamples;
    private boolean excludeIdleSamples;
    private boolean onlyUnsafeAllocationSamples;
    private boolean parseLocations;
    private GraphType graphType;
    private GraphComponents graphComponents;
    private SpanScope spanScope;
    private WeightUnit weightUnit = WeightUnit.NONE;
    private boolean flamegraphOnlyImport;

    public GraphParametersBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public GraphParametersBuilder withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    public GraphParametersBuilder withThreadInfo(ThreadInfo threadInfo) {
        return withThreads(threadInfo == null ? List.of() : List.of(threadInfo));
    }

    /**
     * Scopes the graph to a set of threads — everything behind one collapsed timeline lane.
     */
    public GraphParametersBuilder withThreads(List<ThreadInfo> threads) {
        this.threads = threads == null ? List.of() : List.copyOf(threads);
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

    public GraphParametersBuilder withGraphType(GraphType graphType) {
        this.graphType = graphType;
        return this;
    }

    public GraphParametersBuilder withGraphComponents(GraphComponents components) {
        this.graphComponents = components;
        return this;
    }

    public GraphParametersBuilder withSpanScope(SpanScope spanScope) {
        this.spanScope = spanScope;
        return this;
    }

    public GraphParametersBuilder withWeightUnit(WeightUnit weightUnit) {
        this.weightUnit = weightUnit != null ? weightUnit : WeightUnit.NONE;
        return this;
    }

    public GraphParametersBuilder withFlamegraphOnlyImport(boolean flamegraphOnlyImport) {
        this.flamegraphOnlyImport = flamegraphOnlyImport;
        return this;
    }

    public GraphParameters build() {
        return new GraphParameters(
                eventType,
                timeRange,
                threads,
                searchPattern,
                threadMode,
                collectWeight,
                excludeNonJavaSamples,
                excludeIdleSamples,
                onlyUnsafeAllocationSamples,
                parseLocations,
                graphType,
                graphComponents,
                spanScope,
                weightUnit,
                flamegraphOnlyImport);
    }
}
