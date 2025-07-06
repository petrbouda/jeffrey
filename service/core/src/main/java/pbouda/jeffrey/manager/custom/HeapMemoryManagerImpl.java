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

package pbouda.jeffrey.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.manager.builder.HeapMemoryOverviewEventBuilder;
import pbouda.jeffrey.manager.builder.AllocationAnalysisEventBuilder;
import pbouda.jeffrey.manager.custom.model.heap.HeapMemoryOverviewData;
import pbouda.jeffrey.manager.custom.model.heap.MemoryPoolTimelines;
import pbouda.jeffrey.manager.custom.model.heap.AllocationStatistics;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.List;

public class HeapMemoryManagerImpl implements HeapMemoryManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;

    public HeapMemoryManagerImpl(ProfileInfo profileInfo, ProfileEventRepository eventRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
    }

    @Override
    public HeapMemoryOverviewData getOverviewData() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        
        // Query heap summary events (all types in one query)
        EventQueryConfigurer heapQuery = new EventQueryConfigurer()
            .withEventTypes(List.of(
                Type.GC_HEAP_SUMMARY,
                Type.G1_HEAP_SUMMARY, 
                Type.PS_HEAP_SUMMARY
            ))
            .withTimeRange(timeRange)
            .withJsonFields();
            
        var heapOverviewBuilder = new HeapMemoryOverviewEventBuilder(timeRange);
        eventRepository.newEventStreamerFactory()
            .newGenericStreamer(heapQuery)
            .startStreaming(heapOverviewBuilder);
            
        // Query allocation events separately
        EventQueryConfigurer allocationQuery = new EventQueryConfigurer()
            .withEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
            .withTimeRange(timeRange)
            .withJsonFields();
            
        AllocationStatistics allocationStats = eventRepository.newEventStreamerFactory()
            .newGenericStreamer(allocationQuery)
            .startStreaming(new AllocationAnalysisEventBuilder(timeRange));
            
        return heapOverviewBuilder.buildWithAllocationStats(allocationStats);
    }

    @Override
    public MemoryPoolTimelines getPoolTimelineData(String timeRange) {
        // For now, return the pool timelines from overview data
        // This can be optimized later with time-range specific queries
        return getOverviewData().poolTimelines();
    }

    @Override
    public AllocationStatistics getAllocationTimelineData(String timeRange) {
        // For now, return the allocation stats from overview data
        // This can be optimized later with time-range specific queries
        return getOverviewData().allocationStats();
    }
}
