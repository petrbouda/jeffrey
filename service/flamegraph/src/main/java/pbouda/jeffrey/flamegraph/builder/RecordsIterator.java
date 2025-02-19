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

import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphComponents;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.flamegraph.api.RawGraphData;
import pbouda.jeffrey.jfrparser.db.QueryBuilder;
import pbouda.jeffrey.jfrparser.db.RecordQuery;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;

public class RecordsIterator implements FlamegraphBuilder {

    private final Config config;
    private final EventsReadRepository eventsReadRepository;
    private final RecordBuilders recordBuilders;

    public RecordsIterator(
            Config config,
            RecordBuilders recordBuilders,
            EventsReadRepository eventsReadRepository) {

        this.config = config;
        this.recordBuilders = recordBuilders;
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public RawGraphData iterator() {
        var timeseriesBuilder = recordBuilders.timeseriesBuilder();
        var frameBuilder = recordBuilders.frameTreeBuilder();

        GraphParameters params = config.graphParameters();

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        QueryBuilder queryBuilder = QueryBuilder.events(config.eventType().resolveGroupedTypes())
                .stacktraces(params.stacktraceTypes())
                .stacktraceTags(params.stacktraceTags())
                .threads(params.threadMode(), config.threadInfo());

        RelativeTimeRange timeRange = config.timeRange();
        if (timeRange.isStartUsed()) {
            queryBuilder = queryBuilder.from(timeRange.start());
        }
        if (timeRange.isEndUsed()) {
            queryBuilder = queryBuilder.until(timeRange.end());
        }

        /*
         * Request data from the repository and build the flamegraph and timeseries.
         */
        RecordQuery recordQuery = queryBuilder.build();
        if (params.graphComponents() == GraphComponents.FLAMEGRAPH_ONLY) {
            eventsReadRepository.streamRecords(recordQuery)
                    .forEach(frameBuilder::onRecord);

            return new RawGraphData(frameBuilder.build());
        } else {
            eventsReadRepository.streamRecords(recordQuery)
                    .forEach(record -> {
                        frameBuilder.onRecord(record);
                        timeseriesBuilder.onRecord(record);
                    });

            return new RawGraphData(frameBuilder.build(), timeseriesBuilder.build());
        }
    }
}
