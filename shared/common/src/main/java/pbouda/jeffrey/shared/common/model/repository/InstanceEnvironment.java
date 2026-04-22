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

package pbouda.jeffrey.shared.common.model.repository;

import java.util.Optional;

/**
 * Strongly typed payload of one-shot JFR events describing the JVM's
 * runtime environment. Each sub-record is optional because older JDKs may
 * not emit a given event (e.g. non-containerised hosts have no
 * ContainerConfiguration, shutdown only appears on FINISHED instances).
 */
public record InstanceEnvironment(
        Optional<JvmInformation> jvm,
        Optional<OsInformation> os,
        Optional<CpuInformation> cpu,
        Optional<GcConfiguration> gc,
        Optional<GcHeapConfiguration> gcHeap,
        Optional<CompilerConfiguration> compiler,
        Optional<ContainerConfiguration> container,
        Optional<VirtualizationInformation> virtualization,
        Optional<ShutdownInfo> shutdown) {

    public record JvmInformation(
            Optional<String> jvmName,
            Optional<String> jvmVersion,
            Optional<String> jvmArguments,
            Optional<String> jvmFlags,
            Optional<String> javaArguments,
            Optional<Long> jvmStartTime,
            Optional<Long> pid) {
    }

    public record OsInformation(
            Optional<String> osVersion) {
    }

    public record CpuInformation(
            Optional<String> cpu,
            Optional<String> description,
            Optional<Integer> sockets,
            Optional<Integer> cores,
            Optional<Integer> hwThreads) {
    }

    public record GcConfiguration(
            Optional<String> youngCollector,
            Optional<String> oldCollector,
            Optional<Integer> parallelGCThreads,
            Optional<Integer> concurrentGCThreads,
            Optional<Boolean> usesDynamicGCThreads,
            Optional<Boolean> isExplicitGCConcurrent,
            Optional<Boolean> isExplicitGCDisabled,
            Optional<Long> pauseTargetMillis,
            Optional<Integer> gcTimeRatio) {
    }

    public record GcHeapConfiguration(
            Optional<Long> minSize,
            Optional<Long> maxSize,
            Optional<Long> initialSize,
            Optional<Boolean> usesCompressedOops,
            Optional<String> compressedOopsMode,
            Optional<Long> objectAlignment,
            Optional<Integer> heapAddressBits) {
    }

    public record CompilerConfiguration(
            Optional<Integer> threadCount,
            Optional<Boolean> tieredCompilation,
            Optional<Boolean> dynamicCompilerThreadCount) {
    }

    public record ContainerConfiguration(
            Optional<String> containerType,
            Optional<Long> cpuSlicePeriod,
            Optional<Long> cpuQuota,
            Optional<Long> cpuShares,
            Optional<Integer> effectiveCpuCount,
            Optional<Long> memorySoftLimit,
            Optional<Long> memoryLimit,
            Optional<Long> swapMemoryLimit,
            Optional<Long> hostTotalMemory,
            Optional<Long> hostTotalSwapMemory) {
    }

    public record VirtualizationInformation(
            Optional<String> name) {
    }

    public record ShutdownInfo(
            Optional<String> reason,
            Optional<Long> eventTime,
            ShutdownKind kind) {
    }

    /**
     * Classification of {@code jdk.Shutdown.reason}. The set of strings that
     * map to each value is fixed in the OpenJDK sources:
     * <ul>
     *   <li>{@code jvm.cpp}, {@code JVM_BeforeHalt} emits
     *       {@code "Shutdown requested from Java"} on every graceful path.</li>
     *   <li>{@code jfrEmergencyDump.cpp}, {@code post_events} emits
     *       {@code "VM Error"} for fatal VM errors and
     *       {@code "CrashOnOutOfMemoryError"} for OOM-triggered crashes on
     *       JDK 24+.</li>
     * </ul>
     * Any other string (third-party JVMs — SapMachine, Graal, …) falls
     * through to {@link #UNKNOWN}. The raw reason is always preserved.
     */
    public enum ShutdownKind {
        /** "Shutdown requested from Java". */
        GRACEFUL,
        /** "VM Error" — fatal VM error, hs_err file usually present. */
        VM_ERROR,
        /** "CrashOnOutOfMemoryError" — -XX:+CrashOnOutOfMemoryError triggered by OOM. */
        CRASH_OOM,
        /** Unknown / third-party reason string. */
        UNKNOWN;

        public static ShutdownKind classify(String reason) {
            if (reason == null) return UNKNOWN;
            return switch (reason) {
                case "Shutdown requested from Java" -> GRACEFUL;
                case "VM Error" -> VM_ERROR;
                case "CrashOnOutOfMemoryError" -> CRASH_OOM;
                default -> UNKNOWN;
            };
        }
    }
}
