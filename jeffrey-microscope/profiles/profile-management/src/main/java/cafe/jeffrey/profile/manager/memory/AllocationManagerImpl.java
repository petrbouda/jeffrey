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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedTypesBuilder;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverviewBuilder;
import cafe.jeffrey.profile.manager.model.allocation.AllocationTimeseriesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
