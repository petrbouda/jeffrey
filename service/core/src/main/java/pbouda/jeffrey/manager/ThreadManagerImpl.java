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

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.builder.ThreadStatisticsBuilder;
import pbouda.jeffrey.manager.model.AllocatingThread;
import pbouda.jeffrey.manager.model.ThreadStats;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.List;

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
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JAVA_THREAD_STATISTICS)
                .withJsonFields();

        ThreadStatisticsBuilder builder =
                new ThreadStatisticsBuilder(new RelativeTimeRange(profileInfo.profilingStartEnd()));

        eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(builder::onRecord);

        return builder.build();
    }

    @Override
    public List<AllocatingThread> threadsAllocatingMemory() {
        return List.of();
    }

    @Override
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }
}
