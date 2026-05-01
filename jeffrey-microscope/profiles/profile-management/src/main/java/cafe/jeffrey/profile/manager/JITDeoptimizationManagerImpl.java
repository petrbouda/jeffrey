/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import cafe.jeffrey.profile.common.event.JITDeoptimizationEvent;
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.profile.common.event.JITDeoptimizationReasonCount;
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.profile.manager.builder.JITDeoptimizationCountTimeseriesBuilder;
import cafe.jeffrey.profile.manager.builder.JITDeoptimizationEventsBuilder;
import cafe.jeffrey.profile.manager.builder.JITDeoptimizationReasonDistributionBuilder;
import cafe.jeffrey.profile.manager.builder.JITDeoptimizationStatsBuilder;
import cafe.jeffrey.profile.manager.builder.JITDeoptimizationTopMethodsBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class JITDeoptimizationManagerImpl implements JITDeoptimizationManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public JITDeoptimizationManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository) {

        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public JITDeoptimizationStats statistics() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        long durationMillis = timeRange.end().minus(timeRange.start()).toMillis();

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DEOPTIMIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new JITDeoptimizationStatsBuilder(durationMillis));
    }

    @Override
    public SingleSerie timeseries() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DEOPTIMIZATION)
                .withTimeRange(timeRange);

        return eventStreamRepository.genericStreaming(
                configurer, new JITDeoptimizationCountTimeseriesBuilder("Deoptimizations", timeRange));
    }

    @Override
    public List<JITDeoptimizationEvent> events(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DEOPTIMIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new JITDeoptimizationEventsBuilder(limit));
    }

    @Override
    public List<JITDeoptimizationMethodAggregate> topMethods(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DEOPTIMIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new JITDeoptimizationTopMethodsBuilder(limit));
    }

    @Override
    public List<JITDeoptimizationReasonCount> reasonDistribution() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DEOPTIMIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new JITDeoptimizationReasonDistributionBuilder());
    }
}
