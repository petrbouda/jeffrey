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

package cafe.jeffrey.profile.manager.model.system;

/**
 * Headline system/host metrics for a profile. All CPU values are in basis points
 * (percent × 100, e.g. {@code 442} = 4.42%) so they survive integer transport.
 *
 * @param avgMachineCpuBp        average total machine CPU load
 * @param maxMachineCpuBp        maximum total machine CPU load
 * @param avgJvmCpuBp            average JVM CPU load (user + system)
 * @param avgOtherCpuBp          average CPU consumed by other processes (machine − JVM)
 * @param maxContextSwitchRateHz maximum observed thread context-switch rate
 * @param processCount           distinct host processes observed alongside the JVM
 * @param networkInterfaceCount  distinct network interfaces with utilization samples
 */
public record SystemOverview(
        long avgMachineCpuBp,
        long maxMachineCpuBp,
        long avgJvmCpuBp,
        long avgOtherCpuBp,
        long maxContextSwitchRateHz,
        int processCount,
        int networkInterfaceCount) {
}
