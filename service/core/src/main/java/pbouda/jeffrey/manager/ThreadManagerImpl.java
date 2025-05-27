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
import pbouda.jeffrey.manager.model.AllocatingThread;
import pbouda.jeffrey.manager.model.ThreadCpuLoads;
import pbouda.jeffrey.manager.model.ThreadStats;
import pbouda.jeffrey.manager.model.ThreadWithCpuLoad;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.math.BigDecimal;
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

        ThreadTimeseriesBuilder builder =
                new ThreadTimeseriesBuilder(new RelativeTimeRange(profileInfo.profilingStartEnd()));

        eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(builder::onRecord);

        return builder.build();
    }

    @Override
    public List<AllocatingThread> threadsAllocatingMemory(int limit) {
        List<GenericRecord> allLatest = eventRepository.allLatest(Type.THREAD_ALLOCATION_STATISTICS);

        List<AllocatingThread> result = allLatest.stream()
                .map(GenericRecord::jsonFields)
                .map(node -> new AllocatingThread(node.get("thread").asText(), node.get("allocated").asLong()))
                .toList();

        return result.stream()
                .sorted(Comparator.comparing(AllocatingThread::allocatedBytes).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public ThreadCpuLoads threadCpuLoads(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_CPU_LOAD)
                .withJsonFields();

        CPULoadBuilder builder = new CPULoadBuilder(limit);

        eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(builder::onRecord);

        ThreadCpuLoads result = builder.build();
        return new ThreadCpuLoads(result.user(), result.system());
    }

    @Override
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }

    // Mock data for User CPU Load threads (max 10)
    private List<ThreadWithCpuLoad> getTopUserCpuThreads() {
        long now = System.currentTimeMillis();
        return List.of(
                new ThreadWithCpuLoad(now - 1000, "main", new BigDecimal("87.5")),
                new ThreadWithCpuLoad(now - 2000, "worker-thread-1", new BigDecimal("73.2")),
                new ThreadWithCpuLoad(now - 3000, "http-nio-8080-exec-1", new BigDecimal("65.8")),
                new ThreadWithCpuLoad(now - 4000, "scheduler-thread-1", new BigDecimal("58.9")),
                new ThreadWithCpuLoad(now - 5000, "database-pool-1", new BigDecimal("52.4")),
                new ThreadWithCpuLoad(now - 6000, "async-processor-2", new BigDecimal("47.1")),
                new ThreadWithCpuLoad(now - 7000, "cache-manager", new BigDecimal("41.6")),
                new ThreadWithCpuLoad(now - 8000, "message-handler-3", new BigDecimal("38.2")),
                new ThreadWithCpuLoad(now - 9000, "timer-thread", new BigDecimal("34.7")),
                new ThreadWithCpuLoad(now - 10000, "worker-thread-2", new BigDecimal("29.3"))
        );
    }

    // Mock data for System CPU Load threads (max 10)
    private List<ThreadWithCpuLoad> getTopSystemCpuThreads() {
        long now = System.currentTimeMillis();
        return List.of(
                new ThreadWithCpuLoad(now - 1500, "GC Thread#0", new BigDecimal("94.2")),
                new ThreadWithCpuLoad(now - 2500, "VM Thread", new BigDecimal("76.8")),
                new ThreadWithCpuLoad(now - 3500, "C2 CompilerThread0", new BigDecimal("68.5")),
                new ThreadWithCpuLoad(now - 4500, "G1 Young RemSet Sampling", new BigDecimal("61.7")),
                new ThreadWithCpuLoad(now - 5500, "G1 Conc#0", new BigDecimal("55.3")),
                new ThreadWithCpuLoad(now - 6500, "VM Periodic Task Thread", new BigDecimal("49.1")),
                new ThreadWithCpuLoad(now - 7500, "GC Thread#1", new BigDecimal("43.8")),
                new ThreadWithCpuLoad(now - 8500, "C1 CompilerThread0", new BigDecimal("37.4")),
                new ThreadWithCpuLoad(now - 9500, "Signal Dispatcher", new BigDecimal("31.9")),
                new ThreadWithCpuLoad(now - 10500, "Finalizer", new BigDecimal("26.5"))
        );
    }
}
