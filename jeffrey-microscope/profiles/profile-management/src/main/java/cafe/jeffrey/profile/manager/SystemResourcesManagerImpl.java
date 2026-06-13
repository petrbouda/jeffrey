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

import cafe.jeffrey.profile.manager.model.system.ContextSwitchTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.CpuLoadStatsBuilder;
import cafe.jeffrey.profile.manager.model.system.CpuLoadTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.NetworkInterfacesBuilder;
import cafe.jeffrey.profile.manager.model.system.NetworkRateTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.manager.model.system.SystemProcessInfo;
import cafe.jeffrey.profile.manager.model.system.SystemProcessesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class SystemResourcesManagerImpl implements SystemResourcesManager {

    private static final String NETWORK_INTERFACE_FIELD = "networkInterface";

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public SystemResourcesManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public SystemOverview overview() {
        EventQueryConfigurer cpuConfigurer = new EventQueryConfigurer()
                .withEventType(Type.CPU_LOAD)
                .withJsonFields();
        CpuLoadStatsBuilder.CpuLoadStats cpuStats =
                eventStreamRepository.genericStreaming(cpuConfigurer, new CpuLoadStatsBuilder());

        long maxContextSwitchRate = maxSerieValue(contextSwitchTimeline());

        return new SystemOverview(
                cpuStats.avgMachineBp(),
                cpuStats.maxMachineBp(),
                cpuStats.avgJvmBp(),
                cpuStats.avgOtherBp(),
                maxContextSwitchRate,
                processes().size(),
                networkInterfaces().size());
    }

    @Override
    public TimeseriesData cpuTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.CPU_LOAD)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new CpuLoadTimeseriesBuilder(timeRange));
    }

    @Override
    public List<String> networkInterfaces() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NETWORK_UTILIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new NetworkInterfacesBuilder());
    }

    @Override
    public TimeseriesData networkTimeline(String networkInterface) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.NETWORK_UTILIZATION)
                .withJsonFields()
                .withJsonFieldEquals(NETWORK_INTERFACE_FIELD, networkInterface);

        return eventStreamRepository.genericStreaming(configurer, new NetworkRateTimeseriesBuilder(timeRange));
    }

    @Override
    public TimeseriesData contextSwitchTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_CONTEXT_SWITCH_RATE)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ContextSwitchTimeseriesBuilder(timeRange));
    }

    @Override
    public List<SystemProcessInfo> processes() {
        // Last snapshot wins per pid — requires a chronological stream.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.SYSTEM_PROCESS)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new SystemProcessesBuilder());
    }

    private static long maxSerieValue(TimeseriesData data) {
        if (data.series().isEmpty()) {
            return 0;
        }
        SingleSerie serie = data.series().getFirst();
        return serie.data().stream().mapToLong(point -> point.get(1)).max().orElse(0);
    }
}
