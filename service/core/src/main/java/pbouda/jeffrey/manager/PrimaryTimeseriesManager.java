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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.QueryBuilder;
import pbouda.jeffrey.timeseries.PathMatchingTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SearchableTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;

public class PrimaryTimeseriesManager implements TimeseriesManager {

    private final RelativeTimeRange timeRange;
    private final ProfileEventRepository eventRepository;

    public PrimaryTimeseriesManager(
            ProfilingStartEnd profilingStartEnd,
            ProfileEventRepository eventRepository) {

        this.timeRange = new RelativeTimeRange(profilingStartEnd);
        this.eventRepository = eventRepository;
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        RecordBuilder<? super StackBasedRecord, TimeseriesData> builder;

        if (generate.graphParameters().searchPattern() != null) {
            builder = new SearchableTimeseriesBuilder(timeRange, generate.graphParameters().searchPattern());
        } else if (!generate.markers().isEmpty()) {
            builder = new PathMatchingTimeseriesBuilder(timeRange, generate.markers());
        } else {
            builder = new SimpleTimeseriesBuilder(timeRange);
        }

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        QueryBuilder queryBuilder = eventRepository.newQueryBuilder(generate.eventType().resolveGroupedTypes());
        if (timeRange.isStartUsed()) {
            queryBuilder = queryBuilder.from(timeRange.start());
        }
        if (timeRange.isEndUsed()) {
            queryBuilder = queryBuilder.until(timeRange.end());
        }

        eventRepository.streamRecords(queryBuilder.build())
                .forEach(builder::onRecord);

        return builder.build();
    }
}
