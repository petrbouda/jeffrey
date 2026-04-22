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

export interface JvmInformation {
  jvmName?: string;
  jvmVersion?: string;
  jvmArguments?: string;
  jvmFlags?: string;
  javaArguments?: string;
  jvmStartTime?: number; // epoch millis
  pid?: number;
}

export interface OsInformation {
  osVersion?: string;
}

export interface CpuInformation {
  cpu?: string;
  description?: string;
  sockets?: number;
  cores?: number;
  hwThreads?: number;
}

export interface GcConfiguration {
  youngCollector?: string;
  oldCollector?: string;
  parallelGcThreads?: number;
  concurrentGcThreads?: number;
  usesDynamicGcThreads?: boolean;
  isExplicitGcConcurrent?: boolean;
  isExplicitGcDisabled?: boolean;
  pauseTargetMillis?: number;
  gcTimeRatio?: number;
}

export interface GcHeapConfiguration {
  minSize?: number;
  maxSize?: number;
  initialSize?: number;
  usesCompressedOops?: boolean;
  compressedOopsMode?: string;
  objectAlignment?: number;
  heapAddressBits?: number;
}

export interface CompilerConfiguration {
  threadCount?: number;
  tieredCompilation?: boolean;
  dynamicCompilerThreadCount?: boolean;
}

export interface ContainerConfiguration {
  containerType?: string;
  cpuSlicePeriod?: number;
  cpuQuota?: number;
  cpuShares?: number;
  effectiveCpuCount?: number;
  memorySoftLimit?: number;
  memoryLimit?: number;
  swapMemoryLimit?: number;
  hostTotalMemory?: number;
  hostTotalSwapMemory?: number;
}

export interface VirtualizationInformation {
  name?: string;
}

/**
 * Classification of {@code jdk.Shutdown.reason}. Server derives this from a
 * fixed set of HotSpot-emitted strings — see the OpenJDK sources
 * {@code jvm.cpp::JVM_BeforeHalt} and
 * {@code jfrEmergencyDump.cpp::post_events}. Any unrecognised reason
 * (third-party JVMs) falls through to {@code UNKNOWN} and the raw reason
 * string is still preserved alongside.
 */
export type ShutdownKind = 'GRACEFUL' | 'VM_ERROR' | 'CRASH_OOM' | 'UNKNOWN';

export interface ShutdownInfo {
  reason?: string;
  eventTime?: number;
  kind?: ShutdownKind;
}

export default interface InstanceEnvironment {
  jvm?: JvmInformation;
  os?: OsInformation;
  cpu?: CpuInformation;
  gc?: GcConfiguration;
  gcHeap?: GcHeapConfiguration;
  compiler?: CompilerConfiguration;
  container?: ContainerConfiguration;
  virtualization?: VirtualizationInformation;
  shutdown?: ShutdownInfo;
}
