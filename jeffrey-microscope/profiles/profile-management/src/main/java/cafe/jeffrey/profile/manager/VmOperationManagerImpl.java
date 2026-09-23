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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.vmoperation.PauseTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointSyncTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStatsBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.Comparator;
import java.util.List;

public class VmOperationManagerImpl implements VmOperationManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public VmOperationManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public VmOverview overview() {
        List<VmOperationStat> vmOperations = vmOperations();
        long vmOperationCount = vmOperations.stream().mapToLong(VmOperationStat::count).sum();
        long totalSafepointPause = vmOperations.stream()
                .filter(VmOperationStat::safepoint)
                .mapToLong(VmOperationStat::totalNanos)
                .sum();
        VmOperationStat longest = vmOperations.stream()
                .max(Comparator.comparingLong(VmOperationStat::maxNanos))
                .orElse(null);

        boolean hasTimeToSafepoint = eventRepository.containsEventType(Type.SAFEPOINT_STATE_SYNCHRONIZATION);
        boolean hasSafepointOffenders = eventRepository.containsEventType(Type.SAFEPOINT_LATENCY);

        return new VmOverview(
                vmOperationCount,
                totalSafepointPause,
                longest == null ? 0 : longest.maxNanos(),
                longest == null ? null : longest.operation(),
                !vmOperations.isEmpty(),
                hasTimeToSafepoint,
                hasSafepointOffenders);
    }

    @Override
    public List<VmOperationStat> vmOperations() {
        if (!eventRepository.containsEventType(Type.EXECUTE_VM_OPERATION)) {
            return List.of();
        }
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXECUTE_VM_OPERATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new VmOperationStatsBuilder());
    }

    @Override
    public TimeseriesData pausesTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXECUTE_VM_OPERATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new PauseTimeseriesBuilder(timeRange));
    }

    @Override
    public TimeseriesData timeToSafepointTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.SAFEPOINT_STATE_SYNCHRONIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new SafepointSyncTimeseriesBuilder(timeRange));
    }

    @Override
    public SafepointLatencyData safepointOffenders() {
        if (!eventRepository.containsEventType(Type.SAFEPOINT_LATENCY)) {
            return SafepointLatencyData.EMPTY;
        }

        // withThreads() because the thread is the answer here, not a detail of it; withJsonFields()
        // for threadState, which is what turns "slow" into "slow for this reason".
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.SAFEPOINT_LATENCY)
                .withThreads()
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new SafepointLatencyBuilder());
    }
}
