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

package cafe.jeffrey.flamegraph.provider;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesResolver;
import cafe.jeffrey.timeseries.TimeseriesSearchBuilder;
import cafe.jeffrey.timeseries.TimeseriesType;

public class TimeseriesDataProvider {

    private static final String SPAN_GENERATE = "timeseries.generate";
    private static final String SPAN_QUERY = "timeseries.query";

    private final ProfileEventStreamRepository eventStreamRepository;
    private final TimeseriesType timeseriesType;
    private final GraphParameters graphParameters;

    public TimeseriesDataProvider(ProfileEventStreamRepository eventStreamRepository, GraphParameters graphParameters) {
        this.eventStreamRepository = eventStreamRepository;
        this.graphParameters = graphParameters;
        this.timeseriesType = TimeseriesType.resolve(graphParameters);
    }

    public TimeseriesData provide() {
        return Tracer.call(SPAN_GENERATE, this::query);
    }

    /**
     * Builds the query and runs it. Split out of {@link #provide()} so the DuckDB call sits in its
     * own span: time under {@code timeseries.query} is the database, the remainder of
     * {@code timeseries.generate} is everything else.
     */
    private TimeseriesData query() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(graphParameters.eventType())
                .withTimeRange(graphParameters.timeRange())
                .filterStacktraceTypes(graphParameters.stacktraceTypes())
                .filterStacktraceTags(graphParameters.stacktraceTags())
                .withThreads(graphParameters.threadMode())
                .withWeight(graphParameters.useWeight())
                .withSearchPattern(graphParameters.searchPattern())
                .withSpecifiedThreads(graphParameters.threads())
                .withSpanIntervals(graphParameters.spanIntervals());

        if (timeseriesType == TimeseriesType.SIMPLE) {
            return Tracer.call(SPAN_QUERY, () ->
                    eventStreamRepository.timeseriesStreamer(configurer, TimeseriesResolver.resolve(graphParameters)));
        } else if (timeseriesType == TimeseriesType.SEARCHING) {
            TimeseriesSearchBuilder builder = new TimeseriesSearchBuilder(graphParameters.timeRange());
            return Tracer.call(SPAN_QUERY, () ->
                    eventStreamRepository.timeseriesSearchingStreamer(configurer, builder));
        } else {
            return Tracer.call(SPAN_QUERY, () ->
                    eventStreamRepository.frameBasedTimeseriesStreamer(configurer, TimeseriesResolver.resolve(graphParameters)));
        }
    }
}
