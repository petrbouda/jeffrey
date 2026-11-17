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

package cafe.jeffrey.profile.manager.thread;

import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.SubmitFailure;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.VtHeader;
import cafe.jeffrey.profile.manager.model.virtualthread.VtLifecycleBuilder;
import cafe.jeffrey.profile.manager.model.virtualthread.VtPinningBuilder;
import cafe.jeffrey.profile.manager.model.virtualthread.VtSubmitFailedBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

import java.util.List;

public class VirtualThreadManagerImpl implements VirtualThreadManager {

    private static final int MAX_TOP_THREADS = 20;
    private static final int MAX_SUBMIT_FAILURES = 200;

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public VirtualThreadManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public VirtualThreadData virtualThreadData() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        VtPinningBuilder.Result pinning = eventStreamRepository.genericStreaming(
                new EventQueryConfigurer().withEventType(Type.VIRTUAL_THREAD_PINNED).withJsonFields(),
                new VtPinningBuilder(timeRange, MAX_TOP_THREADS));

        List<SubmitFailure> submitFailures = eventStreamRepository.genericStreaming(
                new EventQueryConfigurer().withEventType(Type.VIRTUAL_THREAD_SUBMIT_FAILED).withJsonFields(),
                new VtSubmitFailedBuilder(MAX_SUBMIT_FAILURES));

        VtLifecycleBuilder.Result lifecycle = eventStreamRepository.genericStreaming(
                new EventQueryConfigurer()
                        .withEventTypes(List.of(Type.VIRTUAL_THREAD_START, Type.VIRTUAL_THREAD_END))
                        .withJsonFields()
                        .orderedByTime(),
                new VtLifecycleBuilder(timeRange));

        VtHeader header = new VtHeader(
                pinning.count(),
                pinning.totalNanos(),
                pinning.maxNanos(),
                submitFailures.size(),
                lifecycle.started(),
                lifecycle.ended(),
                lifecycle.peakLive());

        return new VirtualThreadData(
                header,
                pinning.timeline(),
                pinning.distribution(),
                pinning.topThreads(),
                pinning.reasons(),
                submitFailures,
                lifecycle.timeline());
    }
}
