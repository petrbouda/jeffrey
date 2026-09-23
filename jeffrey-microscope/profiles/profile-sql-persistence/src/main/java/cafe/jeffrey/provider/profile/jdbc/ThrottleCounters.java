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

package cafe.jeffrey.provider.profile.jdbc;

/**
 * The three {@code jdk.ContainerCPUThrottling} fields the throttle-window derivation reads, and the
 * rule for reading them.
 * <p>
 * All three are cgroup {@code cpu.stat} counters, <b>cumulative since the cgroup was created</b>.
 * That is the whole difficulty: the first sample in any window already carries throttling from
 * before the recording started, so only the difference between consecutive samples says anything
 * about the stretch between them, and an absolute value says nothing at all. A container with no CFS
 * quota cannot be throttled and writes all three as null.
 * <p>
 * These names are duplicated from {@code ContainerCpuThrottling} in {@code common-profile}, which
 * this module does not depend on, and the same delta rule is implemented a second time in
 * {@code ContainerCpuThrottlingAnalyzer} for the Containers page. The two readings must agree — a
 * band in a trace and a verdict on that page describing the same window differently is a bug the
 * reader has no way to resolve. If a field is renamed or the rule changes, both move together.
 */
final class ThrottleCounters {

    /** cgroup {@code nr_periods}: CFS periods elapsed, the denominator a ratio is taken against. */
    static final String ELAPSED_SLICES = "cpuElapsedSlices";

    /** cgroup {@code nr_throttled}: CFS periods in which the container was parked. */
    static final String THROTTLED_SLICES = "cpuThrottledSlices";

    /** cgroup {@code throttled_time}: nanoseconds spent parked. */
    static final String THROTTLED_TIME = "cpuThrottledTime";

    private ThrottleCounters() {
    }
}
