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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.builder.CPULoadBuilder;
import pbouda.jeffrey.manager.builder.ThreadTimeseriesBuilder;
import pbouda.jeffrey.manager.model.thread.AllocatingThread;
import pbouda.jeffrey.manager.model.thread.ThreadCpuLoads;
import pbouda.jeffrey.manager.model.thread.ThreadStats;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ThreadManagerImpl implements ThreadManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ThreadInfoProvider threadInfoProvider;

    public ThreadManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ThreadInfoProvider threadInfoProvider) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.threadInfoProvider = threadInfoProvider;
    }

    @Override
    public ThreadStats threadStatistics() {
        Optional<GenericRecord> latestOpt = eventRepository.latest(Type.JAVA_THREAD_STATISTICS);
        if (latestOpt.isEmpty()) {
            return null;
        }

        GenericRecord latest = latestOpt.get();
        ObjectNode jsonNodes = latest.jsonFields();
        long currAccumulated = jsonNodes.get("accumulatedCount").asLong();
        long currPeak = jsonNodes.get("peakCount").asLong();

        List<EventSummary> summaries = eventTypeRepository.eventSummaries(
                List.of(Type.THREAD_SLEEP, Type.THREAD_PARK, Type.JAVA_MONITOR_ENTER));

        EventSummary sleepSummary = filterEventSummary(summaries, Type.THREAD_SLEEP);
        EventSummary parkSummary = filterEventSummary(summaries, Type.THREAD_PARK);
        EventSummary monitorSummary = filterEventSummary(summaries, Type.JAVA_MONITOR_ENTER);

        return new ThreadStats(
                currAccumulated, currPeak, sleepSummary.samples(), parkSummary.samples(), monitorSummary.samples());
    }

    private EventSummary filterEventSummary(List<EventSummary> summaries, Type type) {
        return summaries.stream()
                .filter(summary -> Type.fromCode(summary.name()).equals(type))
                .findFirst()
                .orElse(null);
    }

    @Override
    public SingleSerie activeThreadsSerie() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JAVA_THREAD_STATISTICS)
                .withJsonFields();

        return eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(new ThreadTimeseriesBuilder(new RelativeTimeRange(profileInfo.profilingStartEnd())));
    }

    @Override
    public List<AllocatingThread> threadsAllocatingMemory(int limit) {
        List<GenericRecord> allLatest = eventRepository.allLatest(Type.THREAD_ALLOCATION_STATISTICS);

        List<AllocatingThread> result = allLatest.stream()
                .map(r -> new AllocatingThread(r.threadInfo(), r.sampleWeight()))
                .toList();

        return result.stream()
                .sorted(Comparator.comparing(AllocatingThread::allocatedBytes).reversed())
                .limit(limit)
                .toList();
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

        ThreadCpuLoads result = eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(new CPULoadBuilder(limit));

        return new ThreadCpuLoads(result.user(), result.system());
    }

    @Override
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }
}
