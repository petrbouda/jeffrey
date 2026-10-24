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

package cafe.jeffrey.profile.perfcounters;

import java.util.List;

/**
 * The headline figures of a perf-data snapshot. Every figure is null when the JVM did not write the
 * counters behind it; durations are nanoseconds, sizes bytes, and points in time epoch milliseconds.
 */
public record PerfCountersOverview(
        Jvm jvm,
        JvmRuntime runtime,
        Memory memory,
        Gc gc,
        Classes classes,
        Safepoints safepoints,
        Jit jit,
        Threads threads) {

    public PerfCountersOverview {
        if (jvm == null || runtime == null || memory == null || gc == null
                || classes == null || safepoints == null || jit == null || threads == null) {
            throw new IllegalArgumentException("Every section of the overview is required");
        }
    }

    public record Jvm(
            String vmName,
            String vmVersion,
            String vmVendor,
            String javaVersion,
            String command,
            String vmArgs,
            String gcPolicy) {
    }

    /** How the uptime was measured. */
    public enum UptimeBasis {
        /** The high-resolution timer the JVM sampled, which older JVMs write. */
        TIMER,
        /** Application time plus safepoint time: the uptime as of the last safepoint. */
        SAFEPOINTS
    }

    /**
     * @param startedAtEpochMs when the JVM started creating itself
     * @param vmInitMillis     how long creating the JVM took
     */
    public record JvmRuntime(
            Long startedAtEpochMs,
            Long vmInitMillis,
            Long uptimeNanos,
            UptimeBasis uptimeBasis,
            Long applicationTimeNanos) {

        private static final long NANOS_PER_MILLI = 1_000_000L;

        /** When the snapshot's JVM was last seen running, or null when start or uptime is unknown. */
        public Long endedAtEpochMs() {
            if (startedAtEpochMs == null || uptimeNanos == null) {
                return null;
            }
            return startedAtEpochMs + uptimeNanos / NANOS_PER_MILLI;
        }
    }

    /** @param usedBytes the used bytes of its spaces together */
    public record Generation(String name, Long usedBytes, Long capacityBytes, Long maxCapacityBytes) {
    }

    public record Pool(Long usedBytes, Long capacityBytes, Long maxCapacityBytes) {
    }

    public record Memory(List<Generation> generations, Pool metaspace, Pool compressedClassSpace) {

        public Memory {
            generations = List.copyOf(generations);
        }
    }

    public record Collector(String name, Long invocations, Long timeNanos) {
    }

    /** @param shareOfUptime the collectors' time over the uptime, from 0 to 1 */
    public record Gc(
            List<Collector> collectors,
            Long invocations,
            Long timeNanos,
            Double shareOfUptime,
            String lastCause) {

        public Gc {
            collectors = List.copyOf(collectors);
        }
    }

    public record Classes(
            Long loaded,
            Long unloaded,
            Long sharedLoaded,
            Long loadedBytes,
            Long loadTimeNanos) {
    }

    public record Safepoints(Long count, Long timeNanos, Long syncTimeNanos) {
    }

    public record Jit(Long compiles, Long bailouts, Long invalidations, Long timeNanos, Long codeBytes) {
    }

    public record Threads(Long live, Long peak, Long daemon, Long started) {
    }
}
