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

import cafe.jeffrey.profile.manager.model.system.ContextSwitchTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.CpuLoadStatsBuilder;
import cafe.jeffrey.profile.manager.model.system.CpuLoadTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.LaunchedProcessInfo;
import cafe.jeffrey.profile.manager.model.system.LaunchedProcessesBuilder;
import cafe.jeffrey.profile.manager.model.system.ModuleEdge;
import cafe.jeffrey.profile.manager.model.system.ModuleExport;
import cafe.jeffrey.profile.manager.model.system.ModuleExportsBuilder;
import cafe.jeffrey.profile.manager.model.system.ModuleRequiresBuilder;
import cafe.jeffrey.profile.manager.model.system.NetworkInterfacesBuilder;
import cafe.jeffrey.profile.manager.model.system.NetworkRateTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.SwapTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.manager.model.system.SystemProcessInfo;
import cafe.jeffrey.profile.manager.model.system.SystemProcessesBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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

    @Override
    public TimeseriesData swapTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.SWAP_SPACE)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new SwapTimeseriesBuilder(timeRange));
    }

    @Override
    public List<LaunchedProcessInfo> launchedProcesses() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.PROCESS_START)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new LaunchedProcessesBuilder());
    }

    @Override
    public List<ModuleEdge> moduleRequires() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.MODULE_REQUIRE)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ModuleRequiresBuilder());
    }

    @Override
    public List<ModuleExport> moduleExports() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.MODULE_EXPORT)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ModuleExportsBuilder());
    }

    private static long maxSerieValue(TimeseriesData data) {
        if (data.series().isEmpty()) {
            return 0;
        }
        SingleSerie serie = data.series().getFirst();
        return serie.data().stream().mapToLong(point -> point.get(1)).max().orElse(0);
    }
}
