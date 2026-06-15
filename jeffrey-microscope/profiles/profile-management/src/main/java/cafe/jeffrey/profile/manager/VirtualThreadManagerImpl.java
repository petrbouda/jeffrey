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

import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.SubmitFailure;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.VtHeader;
import cafe.jeffrey.profile.manager.model.virtualthread.VtLifecycleBuilder;
import cafe.jeffrey.profile.manager.model.virtualthread.VtPinningBuilder;
import cafe.jeffrey.profile.manager.model.virtualthread.VtSubmitFailedBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

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
