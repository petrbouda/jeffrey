/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.common.config;

import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.WeightUnit;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

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
