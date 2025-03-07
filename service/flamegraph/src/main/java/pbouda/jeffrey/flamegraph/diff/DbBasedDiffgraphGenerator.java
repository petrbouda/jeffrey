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

package pbouda.jeffrey.flamegraph.diff;

import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.flamegraph.api.RawGraphData;
import pbouda.jeffrey.flamegraph.builder.RecordBuilders;
import pbouda.jeffrey.flamegraph.builder.RecordBuildersResolver;
import pbouda.jeffrey.flamegraph.builder.RecordsIterator;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.DiffTreeGenerator;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.util.List;
import java.util.function.Supplier;

public class DbBasedDiffgraphGenerator implements GraphGenerator {

    private final ProfileEventRepository primaryEventRepository;
    private final ProfileEventRepository secondaryEventRepository;

    public DbBasedDiffgraphGenerator(
            ProfileEventRepository primaryEventRepository,
            ProfileEventRepository secondaryEventRepository) {

        this.primaryEventRepository = primaryEventRepository;
        this.secondaryEventRepository = secondaryEventRepository;
    }

    @Override
    public GraphData generate(Config config) {
        Supplier<RecordBuilders> recordBuilders;
        if (config.eventType().isAllocationEvent()) {
            recordBuilders = () -> RecordBuildersResolver.allocation(config, true, List.of());
        } else {
            recordBuilders = () -> RecordBuildersResolver.simple(config, true, List.of());
        }

        RawGraphData primaryData = new RecordsIterator(config, recordBuilders.get(), primaryEventRepository)
                .iterate();
        RawGraphData secondaryData = new RecordsIterator(config, recordBuilders.get(), secondaryEventRepository)
                .iterate();

        DiffFrame differentialFrames = new DiffTreeGenerator(primaryData.flamegraph(), secondaryData.flamegraph())
                .generate();
        FlamegraphData flamegraphData = new DiffgraphFormatter(differentialFrames)
                .format();

        TimeseriesData timeseriesData = null;
        if (primaryData.timeseries() != null && secondaryData.timeseries() != null) {
            timeseriesData = TimeseriesUtils.differential(primaryData.timeseries(), secondaryData.timeseries());
        }
        return new GraphData(flamegraphData, timeseriesData);
    }

    @Override
    public GraphData generate(Config config, List<Marker> marker) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
