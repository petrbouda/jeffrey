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

package cafe.jeffrey.profile.manager.memory;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategoriesBuilder;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategoryTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.profile.manager.model.nmt.NmtRssVsTrackedBuilder;
import cafe.jeffrey.profile.manager.model.nmt.NmtTotalStatsBuilder;
import cafe.jeffrey.profile.manager.model.nmt.NmtTotalTimeseriesBuilder;
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

public class NativeMemoryTrackingManagerImpl implements NativeMemoryTrackingManager {

    private static final String RSS_SIZE_FIELD = "size";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public NativeMemoryTrackingManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    private boolean hasNmtData() {
        return eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)
                || eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL);
    }

    @Override
    public NmtOverview overview() {
        if (!hasNmtData()) {
            return NmtOverview.empty();
        }

        List<NmtCategory> categories = categories();
        NmtCategory largest = categories.isEmpty() ? null : categories.getFirst();

        long totalCommitted;
        long totalReserved;
        long peakCommitted;
        if (eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)) {
            NmtTotalStatsBuilder.NmtTotalStats stats = totalStats();
            totalCommitted = stats.lastCommitted();
            totalReserved = stats.lastReserved();
            peakCommitted = stats.peakCommitted();
        } else {
            totalCommitted = categories.stream().mapToLong(NmtCategory::committedBytes).sum();
            totalReserved = categories.stream().mapToLong(NmtCategory::reservedBytes).sum();
            peakCommitted = totalCommitted;
        }

        long untracked = Math.max(0, lastRssBytes() - totalCommitted);

        return new NmtOverview(
                true,
                totalCommitted,
                totalReserved,
                peakCommitted,
                largest == null ? null : largest.category(),
                largest == null ? 0 : largest.committedBytes(),
                categories.size(),
                untracked);
    }

    @Override
    public List<NmtCategory> categories() {
        if (!eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)) {
            return List.of();
        }
        // First/last per category requires a chronological stream.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NATIVE_MEMORY_USAGE)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new NmtCategoriesBuilder());
    }

    @Override
    public TimeseriesData categoryTimeline() {
        if (!eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE)) {
            return TimeseriesData.empty();
        }
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NATIVE_MEMORY_USAGE)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new NmtCategoryTimeseriesBuilder(timeRange));
    }

    @Override
    public TimeseriesData totalTimeline() {
        if (!eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)) {
            return TimeseriesData.empty();
        }
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new NmtTotalTimeseriesBuilder(timeRange));
    }

    @Override
    public TimeseriesData rssVsTrackedTimeline() {
        if (!eventRepository.containsEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)) {
            return TimeseriesData.empty();
        }
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.RESIDENT_SET_SIZE, Type.NATIVE_MEMORY_USAGE_TOTAL))
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new NmtRssVsTrackedBuilder(timeRange));
    }

    private NmtTotalStatsBuilder.NmtTotalStats totalStats() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NATIVE_MEMORY_USAGE_TOTAL)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new NmtTotalStatsBuilder());
    }

    private long lastRssBytes() {
        Optional<ObjectNode> rss = eventRepository.latestJsonFields(Type.RESIDENT_SET_SIZE);
        return rss.map(fields -> Math.max(0, Json.readLong(fields, RSS_SIZE_FIELD))).orElse(0L);
    }
}
