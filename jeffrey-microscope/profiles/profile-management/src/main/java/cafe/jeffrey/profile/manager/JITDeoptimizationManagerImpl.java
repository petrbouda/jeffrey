/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
