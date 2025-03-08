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

package pbouda.jeffrey.frameir.tree;

import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.jfrparser.api.record.SimpleRecord;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.QueryBuilder;
import pbouda.jeffrey.provider.api.repository.RecordQuery;

public class RecordsFrameIterator {

    private final Config config;
    private final ProfileEventRepository eventRepository;

    public RecordsFrameIterator(Config config, ProfileEventRepository eventRepository) {
        this.config = config;
        this.eventRepository = eventRepository;
    }

    public Frame iterate() {
        RecordBuilder<SimpleRecord, Frame> frameBuilder = resolveFrameBuilder(this.config);

        GraphParameters params = config.graphParameters();

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        QueryBuilder queryBuilder = eventRepository.newQueryBuilder(config.eventType().resolveGroupedTypes())
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
        eventRepository.streamRecords(recordQuery)
                .forEach(frameBuilder::onRecord);

        return frameBuilder.build();
    }

    public static RecordBuilder<SimpleRecord, Frame> resolveFrameBuilder(Config config) {
        GraphParameters params = config.graphParameters();
        if (config.eventType().isAllocationEvent()) {
            return new AllocationTreeBuilder(params.threadMode(), params.parseLocations());
        } else if (config.eventType().isBlockingEvent()) {
            return new BlockingTreeBuilder(params.threadMode(), params.parseLocations());
        } else {
            return new SimpleTreeBuilder(params.threadMode(), params.parseLocations());
        }
    }
}
