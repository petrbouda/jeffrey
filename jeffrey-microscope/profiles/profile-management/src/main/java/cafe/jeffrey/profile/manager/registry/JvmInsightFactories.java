/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager.registry;

import cafe.jeffrey.profile.manager.AllocationManager;
import cafe.jeffrey.profile.manager.BlockingManager;
import cafe.jeffrey.profile.manager.ClassLoadingManager;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ExceptionsManager;
import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.NativeMemoryManager;
import cafe.jeffrey.profile.manager.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.SystemResourcesManager;
import cafe.jeffrey.profile.manager.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.HeapDumpManager;
import cafe.jeffrey.profile.manager.HeapMemoryManager;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.profile.manager.SpanManager;
import cafe.jeffrey.profile.manager.ThreadManager;
import cafe.jeffrey.profile.manager.VirtualThreadManager;
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
        SpanManager.Factory span) {
}
