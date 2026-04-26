/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.builder.CPULoadBuilder;
import cafe.jeffrey.profile.manager.builder.ThreadTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.thread.ThreadInfoProvider;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.provider.profile.repository.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.repository.ProfileEventRepository;
import cafe.jeffrey.provider.profile.repository.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.repository.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.model.AllocatingThread;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ThreadManagerImpl implements ThreadManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ThreadInfoProvider threadInfoProvider;

    public ThreadManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ThreadInfoProvider threadInfoProvider) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.threadInfoProvider = threadInfoProvider;
    }

    @Override
    public ThreadStats threadStatistics() {
        Optional<ObjectNode> latestOpt = eventRepository.latestJsonFields(Type.JAVA_THREAD_STATISTICS);
        if (latestOpt.isEmpty()) {
            return null;
        }

        ObjectNode jsonNodes = latestOpt.get();
        long currAccumulated = jsonNodes.get("accumulatedCount").asLong();
        long currPeak = jsonNodes.get("peakCount").asLong();

        List<EventSummary> summaries = eventTypeRepository.eventSummaries(
                List.of(Type.THREAD_SLEEP, Type.THREAD_PARK, Type.JAVA_MONITOR_ENTER));

        long sleepSamples = filterEventSamples(summaries, Type.THREAD_SLEEP);
        long parkSamples = filterEventSamples(summaries, Type.THREAD_PARK);
        long monitorSamples = filterEventSamples(summaries, Type.JAVA_MONITOR_ENTER);

        return new ThreadStats(
                currAccumulated, currPeak, sleepSamples, parkSamples, monitorSamples);
    }

    private long filterEventSamples(List<EventSummary> summaries, Type type) {
        return summaries.stream()
                .filter(summary -> Type.fromCode(summary.name()).equals(type))
                .findFirst()
                .map(EventSummary::samples)
                .orElse(0L);
    }

    @Override
    public SingleSerie activeThreadsSerie() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JAVA_THREAD_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new ThreadTimeseriesBuilder(new RelativeTimeRange(profileInfo.profilingStartEnd())));
    }

    @Override
    public List<AllocatingThread> threadsAllocatingMemory(int limit) {
        return eventRepository.allocatingThreads(limit);
    }

    @Override
    public Type resolveAllocationType() {
        List<EventSummary> summaries = eventTypeRepository.eventSummaries(
                List.of(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_SAMPLE));

        return summaries.stream()
                .max(Comparator.comparing(EventSummary::samples))
                .map(summary -> Type.fromCode(summary.name()))
                .orElse(null);
    }

    @Override
    public ThreadCpuLoads threadCpuLoads(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_CPU_LOAD)
                .withThreads()
                .withJsonFields();

        ThreadCpuLoads result = eventStreamRepository.genericStreaming(configurer, new CPULoadBuilder(limit));

        return new ThreadCpuLoads(result.user(), result.system());
    }

    @Override
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }
}
