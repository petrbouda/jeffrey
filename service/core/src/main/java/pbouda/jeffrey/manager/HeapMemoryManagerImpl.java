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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.model.heap.AllocationTimeseriesBuilder;
import pbouda.jeffrey.manager.model.heap.HeapMemoryOverviewData;
import pbouda.jeffrey.manager.model.heap.HeapMemoryTimeseriesBuilder;
import pbouda.jeffrey.manager.model.heap.HeapMemoryTimeseriesType;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class HeapMemoryManagerImpl implements HeapMemoryManager {

    public static final List<Type> ALLOCATION_EVENT_TYPES = List.of(
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
            Type.OBJECT_ALLOCATION_SAMPLE);

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public HeapMemoryManagerImpl(ProfileInfo profileInfo, ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
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
                    .withEventTypes(ALLOCATION_EVENT_TYPES)
                    .withJsonFields();
        };

        RecordBuilder<GenericRecord, SingleSerie> builder = switch (timeseriesType) {
            case HEAP_BEFORE_AFTER_GC -> new HeapMemoryTimeseriesBuilder(timeRange, timeseriesType);
            case ALLOCATION -> new AllocationTimeseriesBuilder(timeRange, timeseriesType);
        };

        return eventStreamRepository.newEventStreamerFactory(configurer)
                .newGenericStreamer()
                .startStreaming(builder);
    }
}
