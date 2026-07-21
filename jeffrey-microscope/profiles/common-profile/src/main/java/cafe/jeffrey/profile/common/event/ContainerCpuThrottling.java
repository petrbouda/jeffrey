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

package cafe.jeffrey.profile.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single {@code jdk.ContainerCPUThrottling} sample. The three counters are read straight from the
 * kernel cgroup {@code cpu.stat} and are <b>cumulative since cgroup creation</b>, so a per-window rate
 * is obtained by delta-ing consecutive samples. They are null when the container has no CFS quota.
 *
 * <ul>
 *   <li>{@code cpuElapsedSlices} = cgroup {@code nr_periods} — CFS periods elapsed if a quota is set.</li>
 *   <li>{@code cpuThrottledSlices} = cgroup {@code nr_throttled} — periods the container was throttled.</li>
 *   <li>{@code cpuThrottledTime} = cgroup {@code throttled_time} — total nanoseconds spent throttled.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerCpuThrottling(
        Long cpuElapsedSlices,
        Long cpuThrottledSlices,
        Long cpuThrottledTime) {
}
