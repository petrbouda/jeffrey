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

public class GraphParametersBuilder {

    private String searchPattern;
    private boolean threadMode;
    private boolean collectWeight;
    private boolean excludeNonJavaSamples;
    private boolean excludeIdleSamples;
    private boolean onlyUnsafeAllocationSamples;
    private boolean parseLocations;
    private GraphComponents graphComponents;

    public GraphParametersBuilder withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return this;
    }

    public GraphParametersBuilder withThreadMode(boolean threadMode) {
        this.threadMode = threadMode;
        return this;
    }

    public GraphParametersBuilder withCollectWeight(boolean collectWeight) {
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

    public GraphParametersBuilder withGraphComponents(GraphComponents components) {
        this.graphComponents = components;
        return this;
    }

    public GraphParameters build() {
        return new GraphParameters(
                searchPattern,
                threadMode,
                collectWeight,
                excludeNonJavaSamples,
                excludeIdleSamples,
                onlyUnsafeAllocationSamples,
                parseLocations,
                graphComponents);
    }
}
