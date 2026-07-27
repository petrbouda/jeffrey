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

import cafe.jeffrey.profile.manager.model.io.FileForceBuilder;
import cafe.jeffrey.profile.manager.model.io.FileForceStats;
import cafe.jeffrey.profile.manager.model.io.IoDirectoriesBuilder;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimeline;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimelinesBuilder;
import cafe.jeffrey.profile.manager.model.io.IoEndpointsBuilder;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.manager.model.io.IoOverviewBuilder;
import cafe.jeffrey.profile.manager.model.io.IoTargetFilter;
import cafe.jeffrey.profile.manager.model.io.IoThroughputTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.io.SlowestIoBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
    public TimeseriesData throughputTimeline(IoKind kind, IoTargetFilter targetFilter) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(
                configurer, new IoThroughputTimeseriesBuilder(timeRange, targetFilter));
    }

    @Override
    public List<IoEndpointTimeline> endpointTimelines(IoKind kind) {
        if (!hasAny(kind.types())) {
            return List.of();
        }
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(kind.types())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(
                configurer, new IoEndpointTimelinesBuilder(timeRange, MAX_ENDPOINT_TIMELINES));
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
