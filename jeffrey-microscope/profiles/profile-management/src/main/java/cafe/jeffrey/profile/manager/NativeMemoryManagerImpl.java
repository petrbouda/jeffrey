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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.nativememory.DirectBufferTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibrariesBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.profile.manager.model.nativememory.RssStatsBuilder;
import cafe.jeffrey.profile.manager.model.nativememory.RssTimeseriesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.Optional;

public class NativeMemoryManagerImpl implements NativeMemoryManager {

    private static final String DIRECT_BUFFER_COUNT_FIELD = "count";
    private static final String DIRECT_BUFFER_MEMORY_USED_FIELD = "memoryUsed";
    private static final String DIRECT_BUFFER_TOTAL_CAPACITY_FIELD = "totalCapacity";

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
}
