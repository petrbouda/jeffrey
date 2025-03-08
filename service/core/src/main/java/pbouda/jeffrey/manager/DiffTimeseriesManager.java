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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.QueryBuilder;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;

public class DiffTimeseriesManager implements TimeseriesManager {

    private final ProfileEventRepository primaryEventRepository;
    private final ProfileEventRepository secondaryEventRepository;

    private final RelativeTimeRange primaryTimeRange;
    private final RelativeTimeRange secondaryTimeRange;

    public DiffTimeseriesManager(
            ProfilingStartEnd primaryStartEnd,
            ProfilingStartEnd secondaryStartEnd,
            ProfileEventRepository primaryEventRepository,
            ProfileEventRepository secondaryEventRepository) {

        this.primaryEventRepository = primaryEventRepository;
        this.secondaryEventRepository = secondaryEventRepository;

        this.primaryTimeRange = new RelativeTimeRange(primaryStartEnd);
        this.secondaryTimeRange = new RelativeTimeRange(calculateSecondaryStartEnd(primaryStartEnd, secondaryStartEnd));
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        TimeseriesData primaryData = processTimeseries(
                primaryEventRepository,
                generate.eventType(),
                primaryTimeRange);
        TimeseriesData secondaryData = processTimeseries(
                secondaryEventRepository,
                generate.eventType(),
                secondaryTimeRange);

        return TimeseriesUtils.differential(primaryData, secondaryData);
    }

    private static TimeseriesData processTimeseries(
            ProfileEventRepository eventRepository,
            Type eventType,
            RelativeTimeRange timeRange) {

        SimpleTimeseriesBuilder builder = new SimpleTimeseriesBuilder(timeRange);

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        QueryBuilder queryBuilder = eventRepository.newQueryBuilder(eventType.resolveGroupedTypes());
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

    private static ProfilingStartEnd calculateSecondaryStartEnd(
            ProfilingStartEnd primaryStartEnd, ProfilingStartEnd secondaryStartEnd) {

        Duration timeShift = Duration.between(primaryStartEnd.start(), secondaryStartEnd.start());
        return new ProfilingStartEnd(primaryStartEnd.start(), secondaryStartEnd.end().minus(timeShift));
    }
}
