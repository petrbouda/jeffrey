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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.nativememory.DirectBufferTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibrariesBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.profile.manager.model.nativememory.RssStatsBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.RssTimeseriesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.Optional;

public class NativeMemoryManagerImpl implements NativeMemoryManager {

    private static final String DIRECT_BUFFER_COUNT_FIELD = "count";
    private static final String DIRECT_BUFFER_MEMORY_USED_FIELD = "memoryUsed";
    private static final String DIRECT_BUFFER_TOTAL_CAPACITY_FIELD = "totalCapacity";
    private static final int MAX_LIBRARY_OPERATIONS = 500;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public NativeMemoryManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public NativeMemoryOverview overview() {
        // First/last semantics require a chronological stream.
        EventQueryConfigurer rssConfigurer = new EventQueryConfigurer()
                .withEventType(Type.RESIDENT_SET_SIZE)
                .withJsonFields()
                .orderedByTime();
        RssStatsBuilder.RssStats rssStats = eventStreamRepository.genericStreaming(rssConfigurer, new RssStatsBuilder());

        Optional<ObjectNode> directBuffers = eventRepository.latestJsonFields(Type.DIRECT_BUFFER_STATISTICS);
        long bufferCount = directBuffers.map(fields -> Math.max(0, Json.readLong(fields, DIRECT_BUFFER_COUNT_FIELD))).orElse(0L);
        long bufferMemoryUsed = directBuffers.map(fields -> Math.max(0, Json.readLong(fields, DIRECT_BUFFER_MEMORY_USED_FIELD))).orElse(0L);
        long bufferTotalCapacity = directBuffers.map(fields -> Math.max(0, Json.readLong(fields, DIRECT_BUFFER_TOTAL_CAPACITY_FIELD))).orElse(0L);

        return new NativeMemoryOverview(
                rssStats.peakRss(),
                rssStats.lastRss(),
                rssStats.lastRss() - rssStats.firstRss(),
                bufferCount,
                bufferMemoryUsed,
                bufferTotalCapacity,
                nativeLibraries().size());
    }

    @Override
    public TimeseriesData rssTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.RESIDENT_SET_SIZE, Type.GC_HEAP_SUMMARY))
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new RssTimeseriesBuilder(timeRange));
    }

    @Override
    public TimeseriesData directBufferTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.DIRECT_BUFFER_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new DirectBufferTimeseriesBuilder(timeRange));
    }

    @Override
    public List<NativeLibraryInfo> nativeLibraries() {
        // Last snapshot wins per library — requires a chronological stream.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NATIVE_LIBRARY)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new NativeLibrariesBuilder());
    }

    @Override
    public NativeLibraryActivityData nativeLibraryActivity() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.NATIVE_LIBRARY_LOAD, Type.NATIVE_LIBRARY_UNLOAD))
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new NativeLibraryActivityBuilder(timeRange, MAX_LIBRARY_OPERATIONS));
    }
}
