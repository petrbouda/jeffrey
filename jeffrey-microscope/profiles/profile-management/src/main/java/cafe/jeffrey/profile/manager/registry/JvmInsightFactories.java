/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager.registry;

import cafe.jeffrey.profile.manager.memory.AllocationManager;
import cafe.jeffrey.profile.manager.BlockingManager;
import cafe.jeffrey.profile.manager.ClassLoadingManager;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ExceptionsManager;
import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.SecurityManager;
import cafe.jeffrey.profile.manager.SystemResourcesManager;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.memory.HeapMemoryManager;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.profile.manager.SpanManager;
import cafe.jeffrey.profile.manager.TraceAttributesManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.profile.manager.VmOperationManager;

public record JvmInsightFactories(
        GarbageCollectionManager.Factory gc,
        JITCompilationManager.Factory jitCompilation,
        JITDeoptimizationManager.Factory jitDeoptimization,
        HeapMemoryManager.Factory heapMemory,
        ContainerManager.Factory container,
        ThreadManager.Factory thread,
        HeapDumpManager.Factory heapDump,
        ClassLoadingManager.Factory classLoading,
        ExceptionsManager.Factory exceptions,
        NativeMemoryManager.Factory nativeMemory,
        NativeMemoryTrackingManager.Factory nativeMemoryTracking,
        SystemResourcesManager.Factory systemResources,
        VmOperationManager.Factory vmOperation,
        BlockingManager.Factory blocking,
        VirtualThreadManager.Factory virtualThread,
        IoManager.Factory io,
        AllocationManager.Factory allocation,
        LeakCandidatesManager.Factory leakCandidates,
        SecurityManager.Factory security,
        SpanManager.Factory span,
        TraceManager.Factory trace,
        TraceAttributesManager.Factory traceAttributes) {
}
