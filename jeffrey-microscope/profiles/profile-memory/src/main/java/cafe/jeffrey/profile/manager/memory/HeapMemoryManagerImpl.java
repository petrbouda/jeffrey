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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.model.heap.HeapAllocationTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.heap.HeapMemoryOverviewData;
import cafe.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesType;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class HeapMemoryManagerImpl implements HeapMemoryManager {

    public static final List<Type> TLAB_ALLOCATION_EVENT_TYPES = List.of(
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    public static final List<Type> SAMPLED_ALLOCATION_EVENT_TYPES = List.of(
            Type.OBJECT_ALLOCATION_SAMPLE);

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public HeapMemoryManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public HeapMemoryOverviewData getOverviewData() {
        return null;
    }

    @Override
    public SingleSerie timeseries(HeapMemoryTimeseriesType timeseriesType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = switch (timeseriesType) {
            case HEAP_BEFORE_AFTER_GC -> new EventQueryConfigurer()
                    .withEventType(Type.GC_HEAP_SUMMARY)
                    .withJsonFields();
            case ALLOCATION -> new EventQueryConfigurer()
                    .withEventTypes(resolveAllocationEventTypes())
                    .withJsonFields();
        };

        RecordBuilder<GenericRecord, SingleSerie> builder = switch (timeseriesType) {
            case HEAP_BEFORE_AFTER_GC -> new HeapMemoryTimeseriesBuilder(timeRange, timeseriesType);
            case ALLOCATION -> new HeapAllocationTimeseriesBuilder(timeRange, timeseriesType);
        };

        return eventStreamRepository.genericStreaming(configurer, builder);
    }

    /**
     * TLAB events and {@code ObjectAllocationSample} describe the same allocations from two
     * different sources — summing both double-counts when a recording carries both. Prefer the
     * TLAB pair (precise) and fall back to the sampled events only when the TLAB pair is absent.
     */
    private List<Type> resolveAllocationEventTypes() {
        boolean tlabEventsPresent = eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                || eventRepository.containsEventType(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
        return tlabEventsPresent ? TLAB_ALLOCATION_EVENT_TYPES : SAMPLED_ALLOCATION_EVENT_TYPES;
    }
}
