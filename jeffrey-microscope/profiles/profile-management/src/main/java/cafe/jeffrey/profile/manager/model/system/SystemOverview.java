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
