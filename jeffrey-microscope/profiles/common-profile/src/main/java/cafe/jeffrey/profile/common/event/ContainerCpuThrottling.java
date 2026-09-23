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
