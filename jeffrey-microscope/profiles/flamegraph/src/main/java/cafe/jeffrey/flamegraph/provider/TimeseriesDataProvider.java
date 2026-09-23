/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                .withSpanScope(graphParameters.spanScope());

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
