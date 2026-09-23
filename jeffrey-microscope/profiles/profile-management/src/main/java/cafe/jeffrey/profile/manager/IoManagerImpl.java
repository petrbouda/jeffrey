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

import cafe.jeffrey.profile.manager.model.io.FileForceBuilder;
import cafe.jeffrey.profile.manager.model.io.FileForceStats;
import cafe.jeffrey.profile.manager.model.io.IoDirectoriesBuilder;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimeline;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimelinesBuilder;
import cafe.jeffrey.profile.manager.model.io.IoEndpointsBuilder;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoMetric;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.manager.model.io.IoOverviewBuilder;
import cafe.jeffrey.profile.manager.model.io.IoTargetFilter;
import cafe.jeffrey.profile.manager.model.io.IoTimelineTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.io.SlowestIoBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class IoManagerImpl implements IoManager {

    private static final int MAX_SLOWEST_OPERATIONS = 50;
    private static final int MAX_SLOWEST_FORCES = 50;
    /** Sparkline tiles the peer gallery renders — enough to spot an outlier, few enough to stay fast. */
    private static final int MAX_ENDPOINT_TIMELINES = 12;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public IoManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public IoOverview overview(IoKind kind) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new IoOverviewBuilder());
    }

    @Override
    public TimeseriesData timeline(IoKind kind, IoTargetFilter targetFilter) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(
                configurer, new IoTimelineTimeseriesBuilder(timeRange, targetFilter));
    }

    @Override
    public List<IoEndpointTimeline> endpointTimelines(IoKind kind, IoMetric metric) {
        if (!hasAny(kind.types())) {
            return List.of();
        }
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(
                configurer, new IoEndpointTimelinesBuilder(timeRange, MAX_ENDPOINT_TIMELINES, metric));
    }

    @Override
    public List<IoOperation> slowestOperations(IoKind kind) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new SlowestIoBuilder(MAX_SLOWEST_OPERATIONS));
    }

    @Override
    public List<IoEndpoint> endpoints(IoKind kind) {
        if (!hasAny(kind.types())) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new IoEndpointsBuilder());
    }

    @Override
    public List<IoEndpoint> directories() {
        if (!hasAny(IoKind.FILE.types())) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(IoKind.FILE.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new IoDirectoriesBuilder());
    }

    @Override
    public FileForceStats fileForce() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.FILE_FORCE)
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new FileForceBuilder(MAX_SLOWEST_FORCES));
    }

    private boolean hasAny(List<Type> types) {
        return types.stream().anyMatch(eventRepository::containsEventType);
    }
}
