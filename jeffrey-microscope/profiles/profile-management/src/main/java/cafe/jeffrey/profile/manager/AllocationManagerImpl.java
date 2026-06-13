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

import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedTypesBuilder;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverviewBuilder;
import cafe.jeffrey.profile.manager.model.allocation.AllocationTimeseriesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class AllocationManagerImpl implements AllocationManager {

    private static final int MAX_TOP_TYPES = 100;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public AllocationManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    /** Prefer per-allocation TLAB events; fall back to the sampled event when TLAB events are absent. */
    private boolean tlabPresent() {
        return eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                || eventRepository.containsEventType(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
    }

    private List<Type> allocationTypes() {
        return tlabPresent()
                ? HeapMemoryManagerImpl.TLAB_ALLOCATION_EVENT_TYPES
                : HeapMemoryManagerImpl.SAMPLED_ALLOCATION_EVENT_TYPES;
    }

    @Override
    public AllocationOverview overview() {
        boolean sampled = !tlabPresent();
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(allocationTypes())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new AllocationOverviewBuilder(sampled));
    }

    @Override
    public TimeseriesData timeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(allocationTypes())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new AllocationTimeseriesBuilder(timeRange));
    }

    @Override
    public List<AllocatedType> topTypes() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(allocationTypes())
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new AllocatedTypesBuilder(MAX_TOP_TYPES));
    }
}
