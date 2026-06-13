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

import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.BlockingTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStatsBuilder;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStatsBuilder;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadsBuilder;
import cafe.jeffrey.profile.manager.model.blocking.SleepStat;
import cafe.jeffrey.profile.manager.model.blocking.SleepStatsBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class BlockingManagerImpl implements BlockingManager {

    private static final int MAX_PINNED_ENTRIES = 100;

    private static final String MONITOR_CLASS_FIELD = "monitorClass";
    private static final String PARKED_CLASS_FIELD = "parkedClass";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public BlockingManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public BlockingOverview overview() {
        List<ContentionStat> monitors = monitorContention();
        long totalBlocked = monitors.stream().mapToLong(ContentionStat::totalNanos).sum();

        List<ContentionStat> parks = threadParks();
        long parkCount = parks.stream().mapToLong(ContentionStat::count).sum();

        List<PinnedThreadEntry> pinned = pinnedThreads();

        List<MonitorWaitStat> waits = monitorWaits();
        long waitCount = waits.stream().mapToLong(MonitorWaitStat::count).sum();

        List<SleepStat> sleeps = sleeps();
        long sleepCount = sleeps.stream().mapToLong(SleepStat::count).sum();

        return new BlockingOverview(
                monitors.size(),
                totalBlocked,
                waitCount,
                parkCount,
                sleepCount,
                pinned.size(),
                !monitors.isEmpty(),
                !waits.isEmpty(),
                !parks.isEmpty(),
                !sleeps.isEmpty(),
                !pinned.isEmpty());
    }

    @Override
    public TimeseriesData blockingTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.JAVA_MONITOR_ENTER,
                        Type.JAVA_MONITOR_WAIT,
                        Type.THREAD_PARK,
                        Type.THREAD_SLEEP,
                        Type.VIRTUAL_THREAD_PINNED))
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new BlockingTimeseriesBuilder(timeRange));
    }

    @Override
    public List<ContentionStat> monitorContention() {
        return contentionStats(Type.JAVA_MONITOR_ENTER, MONITOR_CLASS_FIELD);
    }

    @Override
    public List<ContentionStat> threadParks() {
        return contentionStats(Type.THREAD_PARK, PARKED_CLASS_FIELD);
    }

    private List<ContentionStat> contentionStats(Type eventType, String classField) {
        if (!eventRepository.containsEventType(eventType)) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(eventType)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ContentionStatsBuilder(classField));
    }

    @Override
    public List<PinnedThreadEntry> pinnedThreads() {
        if (!eventRepository.containsEventType(Type.VIRTUAL_THREAD_PINNED)) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.VIRTUAL_THREAD_PINNED)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new PinnedThreadsBuilder(MAX_PINNED_ENTRIES));
    }

    @Override
    public List<MonitorWaitStat> monitorWaits() {
        if (!eventRepository.containsEventType(Type.JAVA_MONITOR_WAIT)) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JAVA_MONITOR_WAIT)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new MonitorWaitStatsBuilder());
    }

    @Override
    public List<SleepStat> sleeps() {
        if (!eventRepository.containsEventType(Type.THREAD_SLEEP)) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_SLEEP)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new SleepStatsBuilder());
    }
}
