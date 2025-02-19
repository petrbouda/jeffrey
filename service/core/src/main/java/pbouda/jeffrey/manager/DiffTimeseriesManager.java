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
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.db.QueryBuilder;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;

public class DiffTimeseriesManager implements TimeseriesManager {

    private final EventsReadRepository primaryEventsReadRepository;
    private final EventsReadRepository secondaryEventsReadRepository;

    private final RelativeTimeRange primaryTimeRange;
    private final RelativeTimeRange secondaryTimeRange;

    public DiffTimeseriesManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            EventsReadRepository primaryEventsReadRepository,
            EventsReadRepository secondaryEventsReadRepository) {

        this.primaryEventsReadRepository = primaryEventsReadRepository;
        this.secondaryEventsReadRepository = secondaryEventsReadRepository;

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                primaryProfileInfo.startedAt(), primaryProfileInfo.finishedAt());
        ProfilingStartEnd secondaryStartEnd = new ProfilingStartEnd(
                secondaryProfileInfo.startedAt(), secondaryProfileInfo.finishedAt());

        this.primaryTimeRange = new RelativeTimeRange(primaryStartEnd);
        this.secondaryTimeRange = new RelativeTimeRange(calculateSecondaryStartEnd(primaryStartEnd, secondaryStartEnd));
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        TimeseriesData primaryData = processTimeseries(
                primaryEventsReadRepository, generate.eventType(), primaryTimeRange);
        TimeseriesData secondaryData = processTimeseries(
                secondaryEventsReadRepository, generate.eventType(), secondaryTimeRange);

        return TimeseriesUtils.differential(primaryData, secondaryData);
    }

    private static TimeseriesData processTimeseries(
            EventsReadRepository eventsReadRepository,
            Type eventType,
            RelativeTimeRange timeRange) {

        SimpleTimeseriesBuilder builder = new SimpleTimeseriesBuilder(timeRange);

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        QueryBuilder queryBuilder = QueryBuilder.events(eventType.resolveGroupedTypes());
        if (timeRange.isStartUsed()) {
            queryBuilder = queryBuilder.from(timeRange.start());
        }
        if (timeRange.isEndUsed()) {
            queryBuilder = queryBuilder.until(timeRange.end());
        }

        eventsReadRepository.streamRecords(queryBuilder.build())
                .forEach(builder::onRecord);

        return builder.build();
    }

    private static ProfilingStartEnd calculateSecondaryStartEnd(
            ProfilingStartEnd primaryStartEnd, ProfilingStartEnd secondaryStartEnd) {

        Duration timeShift = Duration.between(primaryStartEnd.start(), secondaryStartEnd.start());
        return new ProfilingStartEnd(primaryStartEnd.start(), secondaryStartEnd.end().minus(timeShift));
    }
}
