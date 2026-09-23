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

package cafe.jeffrey.profile.manager.model.container;

import cafe.jeffrey.profile.common.event.ContainerCpuThrottling;

/**
 * One {@code jdk.ContainerCPUThrottling} sample with its offset (millis since profiling start) and
 * the cumulative kernel counters. Produced by {@link cafe.jeffrey.profile.manager.builder.ContainerCpuThrottlingEventBuilder}
 * and folded into per-window deltas by {@link cafe.jeffrey.profile.manager.ContainerCpuThrottlingAnalyzer}.
 */
public record ThrottlingSample(long timestampMillis, ContainerCpuThrottling counters) {
}
