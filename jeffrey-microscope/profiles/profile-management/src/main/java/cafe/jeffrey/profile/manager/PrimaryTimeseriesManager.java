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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesDownsampler;
import cafe.jeffrey.timeseries.TimeseriesResolver;

public class PrimaryTimeseriesManager implements TimeseriesManager {

    private final RelativeTimeRange timeRange;
    private final ProfileEventStreamRepository eventStreamRepository;

    public PrimaryTimeseriesManager(
            ProfilingStartEnd profilingStartEnd,
            ProfileEventStreamRepository eventStreamRepository) {

        this.timeRange = new RelativeTimeRange(profilingStartEnd);
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        GraphParameters params = generate.graphParameters();

        // The request may restrict the query to a window; otherwise the whole recording is used.
        // The effective range must also reach the builder (via params) so its bucket pre-fill spans
        // exactly the queried range.
        RelativeTimeRange effectiveRange = params.timeRange() != null ? params.timeRange() : timeRange;
        GraphParameters effectiveParams = params.toBuilder().withTimeRange(effectiveRange).build();

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(generate.eventType())
                .withTimeRange(effectiveRange)
                .withWeight(params.useWeight())
                .withThreads(params.threadMode());

        TimeseriesData data = eventStreamRepository.timeseriesStreamer(
                configurer, TimeseriesResolver.resolve(effectiveParams));

        Integer targetBuckets = generate.targetBuckets();
        if (targetBuckets == null) {
            return data;
        }
        return TimeseriesDownsampler.downsample(data, targetBuckets);
    }
}
