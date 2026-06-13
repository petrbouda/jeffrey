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

import cafe.jeffrey.profile.manager.model.vmoperation.PauseTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointSyncTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStatsBuilder;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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

        boolean hasSafepointLatency = eventRepository.containsEventType(Type.SAFEPOINT_STATE_SYNCHRONIZATION);

        return new VmOverview(
                vmOperationCount,
                totalSafepointPause,
                longest == null ? 0 : longest.maxNanos(),
                longest == null ? null : longest.operation(),
                !vmOperations.isEmpty(),
                hasSafepointLatency);
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
}
