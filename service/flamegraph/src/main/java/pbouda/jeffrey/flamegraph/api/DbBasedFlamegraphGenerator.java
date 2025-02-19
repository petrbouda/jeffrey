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

package pbouda.jeffrey.flamegraph.api;

import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.builder.RecordBuilders;
import pbouda.jeffrey.flamegraph.builder.RecordBuildersResolver;
import pbouda.jeffrey.flamegraph.builder.RecordsIterator;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;

import java.util.List;

public class DbBasedFlamegraphGenerator implements GraphGenerator {

    private final EventsReadRepository eventsReadRepository;

    public DbBasedFlamegraphGenerator(EventsReadRepository eventsReadRepository) {
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public GraphData generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public GraphData generate(Config config, List<Marker> markers) {
        RecordBuilders recordBuilders;
        if (config.eventType().isAllocationEvent()) {
            recordBuilders = RecordBuildersResolver.allocation(config, false, markers);
        } else if (config.eventType().isBlockingEvent()) {
            recordBuilders = RecordBuildersResolver.blocking(config, markers);
        } else {
            recordBuilders = RecordBuildersResolver.simple(config, false, markers);
        }

        RawGraphData rawGraphData = new RecordsIterator(config, recordBuilders, eventsReadRepository)
                .iterator();

        FlamegraphData flamegraph = recordBuilders.flameGraphBuilder()
                .build(rawGraphData.flamegraph());

        return new GraphData(flamegraph, rawGraphData.timeseries());
    }
}
