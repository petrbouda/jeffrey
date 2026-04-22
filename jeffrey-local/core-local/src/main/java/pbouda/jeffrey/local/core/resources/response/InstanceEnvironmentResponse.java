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

package pbouda.jeffrey.local.core.resources.response;

/**
 * Frontend-facing representation of the parsed JFR one-shot events.
 * All fields are nullable because older JDKs may not emit a given event
 * (e.g. non-containerised hosts have no ContainerConfiguration). Jackson
 * will omit null fields from the JSON so the TypeScript side can safely
 * check for presence with {@code ?.}.
 */
public record InstanceEnvironmentResponse(
        JvmInformation jvm,
        OsInformation os,
        CpuInformation cpu,
        GcConfiguration gc,
        GcHeapConfiguration gcHeap,
        CompilerConfiguration compiler,
        ContainerConfiguration container,
        VirtualizationInformation virtualization,
        ShutdownInfo shutdown) {

    public record JvmInformation(
            String jvmName,
            String jvmVersion,
            String jvmArguments,
            String jvmFlags,
            String javaArguments,
            Long jvmStartTime,
            Long pid) {
    }

    public record OsInformation(
            String osVersion) {
    }

    public record CpuInformation(
            String cpu,
            String description,
            Integer sockets,
            Integer cores,
            Integer hwThreads) {
    }

    public record GcConfiguration(
            String youngCollector,
            String oldCollector,
            Integer parallelGcThreads,
            Integer concurrentGcThreads,
            Boolean usesDynamicGcThreads,
            Boolean isExplicitGcConcurrent,
            Boolean isExplicitGcDisabled,
            Long pauseTargetMillis,
            Integer gcTimeRatio) {
    }

    public record GcHeapConfiguration(
            Long minSize,
            Long maxSize,
            Long initialSize,
            Boolean usesCompressedOops,
            String compressedOopsMode,
            Long objectAlignment,
            Integer heapAddressBits) {
    }

    public record CompilerConfiguration(
            Integer threadCount,
            Boolean tieredCompilation,
            Boolean dynamicCompilerThreadCount) {
    }

    public record ContainerConfiguration(
            String containerType,
            Long cpuSlicePeriod,
            Long cpuQuota,
            Long cpuShares,
            Integer effectiveCpuCount,
            Long memorySoftLimit,
            Long memoryLimit,
            Long swapMemoryLimit,
            Long hostTotalMemory,
            Long hostTotalSwapMemory) {
    }

    public record VirtualizationInformation(
            String name) {
    }

    public record ShutdownInfo(
            String reason,
            Long eventTime,
            String kind) {
    }
}
