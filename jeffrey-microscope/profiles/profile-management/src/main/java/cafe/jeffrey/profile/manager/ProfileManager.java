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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.memory.AllocationManager;
import cafe.jeffrey.profile.manager.memory.HeapMemoryManager;
import cafe.jeffrey.profile.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.profile.tools.otlp.OtlpExportManager;
import cafe.jeffrey.profile.tools.pprof.PprofExportManager;


import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.function.Function;

public interface ProfileManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ProfileManager> {
    }

    ProfileInfo info();

    ProfileConfigurationManager profileConfigurationManager();

    AutoAnalysisManager autoAnalysisManager();

    FlamegraphManager flamegraphManager();

    DifferentialFlamegraphManager diffFlamegraphManager(ProfileManager secondaryManager);

    SubSecondManager subSecondManager();

    TimeseriesManager timeseriesManager();

    TimeseriesManager diffTimeseriesManager(ProfileManager secondaryManager);

    EventViewerManager eventViewerManager();

    FlagsManager flagsManager();

    SamplerHealthManager samplerHealthManager();

    ThreadManager threadManager();

    JITCompilationManager jitCompilationManager();

    JITDeoptimizationManager jitDeoptimizationManager();

    AdditionalFilesManager additionalFilesManager();

    GarbageCollectionManager gcManager();

    ClassLoadingManager classLoadingManager();

    ExceptionsManager exceptionsManager();

    NativeMemoryManager nativeMemoryManager();

    NativeMemoryTrackingManager nativeMemoryTrackingManager();

    SystemResourcesManager systemResourcesManager();

    VmOperationManager vmOperationManager();

    BlockingManager blockingManager();

    VirtualThreadManager virtualThreadManager();

    IoManager ioManager();

    AllocationManager allocationManager();

    LeakCandidatesManager leakCandidatesManager();

    SecurityManager securityManager();

    ContainerManager containerManager();

    HeapMemoryManager heapMemoryManager();

    HeapDumpManager heapDumpManager();

    SpanManager spanManager();

    TraceManager traceManager();

    TraceAttributesManager traceAttributesManager();

    ProfileFeaturesManager featuresManager();

    ProfileToolsManager toolsManager();

    CollapseFramesManager collapseFramesManager();

    PprofExportManager pprofExportManager();

    OtlpExportManager otlpExportManager();

    ProfileCustomManager custom();

    ProfileInfo updateName(String name);

    void markModified();

    /**
     * Returns the total size of the profile directory in bytes.
     * This includes the DuckDB database and any additional files.
     *
     * @return size in bytes
     */
    long sizeInBytes();

    void delete();
}
