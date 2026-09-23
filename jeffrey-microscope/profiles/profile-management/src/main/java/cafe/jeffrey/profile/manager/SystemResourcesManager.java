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

import cafe.jeffrey.profile.manager.model.system.LaunchedProcessInfo;
import cafe.jeffrey.profile.manager.model.system.ModuleEdge;
import cafe.jeffrey.profile.manager.model.system.ModuleExport;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.manager.model.system.SystemProcessInfo;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * System/host insight for a single profile, built from {@code jdk.CPULoad},
 * {@code jdk.NetworkUtilization}, {@code jdk.ThreadContextSwitchRate} and {@code jdk.SystemProcess}
 * events. The headline analysis is "is it my JVM or the box?" — the gap between machine-total CPU
 * and JVM CPU exposes noisy neighbors, and the host-process table names them.
 */
public interface SystemResourcesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, SystemResourcesManager> {
    }

    /**
     * Headline metrics: avg/max machine CPU, avg JVM and other-process CPU (basis points), max
     * context-switch rate, host-process and network-interface counts.
     */
    SystemOverview overview();

    /**
     * Machine-total vs JVM user/system CPU over the recording, in basis points (percent × 100).
     */
    TimeseriesData cpuTimeline();

    /**
     * Distinct network-interface names with utilization samples.
     */
    List<String> networkInterfaces();

    /**
     * Read/write rates for one interface over the recording, in bytes per second.
     */
    TimeseriesData networkTimeline(String networkInterface);

    /**
     * Thread context-switch rate over the recording, in switches per second.
     */
    TimeseriesData contextSwitchTimeline();

    /**
     * Host processes observed alongside the JVM (latest snapshot per pid).
     */
    List<SystemProcessInfo> processes();

    /**
     * OS swap-space timeline (total and used bytes) from {@code jdk.SwapSpace}. Empty when swap tracking
     * is unavailable on the host.
     */
    TimeseriesData swapTimeline();

    /**
     * Subprocesses the JVM launched during the recording, from {@code jdk.ProcessStart}, in time order.
     */
    List<LaunchedProcessInfo> launchedProcesses();

    /**
     * Module dependency edges from {@code jdk.ModuleRequire} (source requires required), de-duplicated.
     */
    List<ModuleEdge> moduleRequires();

    /**
     * Package exports from {@code jdk.ModuleExport} (package exported to target, or unqualified).
     */
    List<ModuleExport> moduleExports();
}
