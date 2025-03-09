/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.flamegraph.builder;

import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.DurationFormatter;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.BlockingTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.jfrparser.api.record.SimpleRecord;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;
import pbouda.jeffrey.timeseries.PathMatchingTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SearchableTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public abstract class RecordBuildersResolver {

    private static RecordBuilder<? super StackBasedRecord, TimeseriesData> resolveTimeseriesBuilder(
            Config config, List<Marker> markers) {

        GraphParameters params = config.graphParameters();
        if (params.searchPattern() != null) {
            return new SearchableTimeseriesBuilder(config.timeRange(), params.searchPattern(), params.useWeight());
        } else if (!markers.isEmpty()) {
            return new PathMatchingTimeseriesBuilder(config.timeRange(), markers, params.useWeight());
        } else {
            return new SimpleTimeseriesBuilder(config.timeRange(), params.useWeight());
        }
    }

    public static RecordBuilders simple(Config config, boolean handleLambdas, List<Marker> markers) {
        GraphParameters params = config.graphParameters();

        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(!markers.isEmpty());
        RecordBuilder<SimpleRecord, Frame> frameTreeBuilder =
                new SimpleTreeBuilder(handleLambdas, params.threadMode(), params.parseLocations());

        return new RecordBuilders(flameGraphBuilder, frameTreeBuilder, resolveTimeseriesBuilder(config, markers));
    }

    public static RecordBuilders allocation(Config config, boolean handleLambdas, List<Marker> markers) {
        GraphParameters params = config.graphParameters();

        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(
                !markers.isEmpty(), weight -> BytesFormatter.format(weight) + " Allocated");
        RecordBuilder<SimpleRecord, Frame> frameTreeBuilder =
                new AllocationTreeBuilder(
                        handleLambdas, params.threadMode(), params.parseLocations(), new AllocationTopFrameProcessor());

        return new RecordBuilders(flameGraphBuilder, frameTreeBuilder, resolveTimeseriesBuilder(config, markers));
    }

    public static RecordBuilders blocking(Config config, List<Marker> markers) {
        GraphParameters params = config.graphParameters();

        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(
                !markers.isEmpty(), weight -> DurationFormatter.format(weight) + " Blocked");
        RecordBuilder<SimpleRecord, Frame> frameTreeBuilder =
                new BlockingTreeBuilder(params.threadMode(), params.parseLocations());

        return new RecordBuilders(flameGraphBuilder, frameTreeBuilder, resolveTimeseriesBuilder(config, markers));
    }
}
