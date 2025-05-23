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
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.builder.ThreadTimeseriesBuilder;
import pbouda.jeffrey.manager.model.AllocatingThread;
import pbouda.jeffrey.manager.model.ThreadStats;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ThreadManagerImpl implements ThreadManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ThreadInfoProvider threadInfoProvider;

    public ThreadManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ThreadInfoProvider threadInfoProvider) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
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

        return new ThreadStats(currAccumulated, currPeak, 0, 0);
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
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }
}
