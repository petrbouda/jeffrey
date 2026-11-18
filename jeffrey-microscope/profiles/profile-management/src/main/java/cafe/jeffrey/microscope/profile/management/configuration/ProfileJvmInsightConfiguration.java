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

package cafe.jeffrey.microscope.profile.management.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.microscope.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.microscope.profile.management.manager.*;
// Explicit import: disambiguates from java.lang.SecurityManager (both match the wildcard above)
import cafe.jeffrey.microscope.profile.management.manager.SecurityManager;
import cafe.jeffrey.microscope.profile.management.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.AllocationManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.AllocationManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.memory.HeapMemoryManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.HeapMemoryManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.LeakCandidatesManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.memory.NativeMemoryManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.NativeMemoryManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.microscope.profile.management.manager.memory.NativeMemoryTrackingManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.gc.GarbageCollectionManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpFiles;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpSessionCache;
import cafe.jeffrey.microscope.profile.management.manager.registry.JvmInsightFactories;
import cafe.jeffrey.microscope.profile.management.manager.thread.ThreadManager;
import cafe.jeffrey.microscope.profile.management.manager.thread.ThreadManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.thread.VirtualThreadManager;
import cafe.jeffrey.microscope.profile.management.manager.thread.VirtualThreadManagerImpl;
import cafe.jeffrey.microscope.profile.management.thread.CachingThreadProvider;
import cafe.jeffrey.microscope.profile.management.thread.DbBasedThreadProvider;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileDatabase;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileDatabases;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileEventRepository;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileEventTypeRepository;
import cafe.jeffrey.microscope.profile.persistence.api.SpanRepository;
import cafe.jeffrey.microscope.profile.persistence.api.TraceAttributeRepository;
import cafe.jeffrey.microscope.profile.persistence.api.TraceRepository;

import java.nio.file.Path;
import java.time.Clock;

public class ProfileJvmInsightConfiguration {

    private final ProfileDatabases profileDatabases;

    public ProfileJvmInsightConfiguration(
            ProfileDatabases profileDatabases) {
        this.profileDatabases = profileDatabases;
    }

    @Bean
    public JvmInsightFactories jvmInsightFactories(
            GarbageCollectionManager.Factory gcFactory,
            JITCompilationManager.Factory jitCompilationFactory,
            JITDeoptimizationManager.Factory jitDeoptimizationFactory,
            HeapMemoryManager.Factory heapMemoryFactory,
            ContainerManager.Factory containerFactory,
            ThreadManager.Factory threadFactory,
            HeapDumpManager.Factory heapDumpFactory,
            ClassLoadingManager.Factory classLoadingFactory,
            ExceptionsManager.Factory exceptionsFactory,
            NativeMemoryManager.Factory nativeMemoryFactory,
            NativeMemoryTrackingManager.Factory nativeMemoryTrackingFactory,
            SystemResourcesManager.Factory systemResourcesFactory,
            VmOperationManager.Factory vmOperationFactory,
            BlockingManager.Factory blockingFactory,
            VirtualThreadManager.Factory virtualThreadFactory,
            IoManager.Factory ioFactory,
            AllocationManager.Factory allocationFactory,
            LeakCandidatesManager.Factory leakCandidatesFactory,
            SecurityManager.Factory securityFactory,
            SpanManager.Factory spanFactory,
            TraceManager.Factory traceFactory,
            TraceAttributesManager.Factory traceAttributesFactory) {

        return new JvmInsightFactories(
                gcFactory,
                jitCompilationFactory,
                jitDeoptimizationFactory,
                heapMemoryFactory,
                containerFactory,
                threadFactory,
                heapDumpFactory,
                classLoadingFactory,
                exceptionsFactory,
                nativeMemoryFactory,
                nativeMemoryTrackingFactory,
                systemResourcesFactory,
                vmOperationFactory,
                blockingFactory,
                virtualThreadFactory,
                ioFactory,
                allocationFactory,
                leakCandidatesFactory,
                securityFactory,
                spanFactory,
                traceFactory,
                traceAttributesFactory);
    }

    @Bean
    public GarbageCollectionManager.Factory gcManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new GarbageCollectionManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new JITCompilationManagerImpl(
                    profileInfo,
                    profileDb.eventTypes(),
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public JITDeoptimizationManager.Factory jitDeoptimizationManager() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new JITDeoptimizationManagerImpl(
                    profileInfo,
                    profileDb.eventStream());
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            ProfileEventRepository eventRepository = profileDb.events();
            ProfileEventStreamRepository eventStreamRepository = profileDb.eventStream();
            ProfileEventTypeRepository eventTypeRepository = profileDb.eventTypes();

            return new ThreadManagerImpl(
                    profileInfo,
                    eventRepository,
                    eventStreamRepository,
                    eventTypeRepository,
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository),
                            profileDb.cache()));
        };
    }

    @Bean
    public VirtualThreadManager.Factory virtualThreadManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new VirtualThreadManagerImpl(
                    profileInfo,
                    profileDb.eventStream());
        };
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new HeapMemoryManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean(destroyMethod = "close")
    public HeapDumpSessionCache heapDumpSessionCache(Clock clock) {
        return new HeapDumpSessionCache(clock);
    }

    @Bean
    public HeapDumpInitService heapDumpInitService(Clock clock) {
        return new HeapDumpInitService(clock);
    }

    @Bean
    public HeapDumpManager.Factory heapDumpManagerFactory(
            @Qualifier(ProfilesConfiguration.PROFILES_PATH) Path profilesPath,
            HeapDumpSessionCache heapDumpSessionCache,
            OqlEngine oqlEngine) {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new HeapDumpManagerImpl(
                    profileInfo,
                    HeapDumpFiles.inProfileDir(profilesPath.resolve(profileInfo.id())),
                    heapDumpSessionCache,
                    oqlEngine,
                    profileDb.pipelineRuns());
        };
    }

    @Bean
    public LeakCandidatesManager.Factory leakCandidatesManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new LeakCandidatesManagerImpl(profileDb.events());
        };
    }

    @Bean
    public ClassLoadingManager.Factory classLoadingManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ClassLoadingManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public ExceptionsManager.Factory exceptionsManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ExceptionsManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream(),
                    profileDb.exceptions());
        };
    }

    @Bean
    public NativeMemoryManager.Factory nativeMemoryManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new NativeMemoryManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public NativeMemoryTrackingManager.Factory nativeMemoryTrackingManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new NativeMemoryTrackingManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public SystemResourcesManager.Factory systemResourcesManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new SystemResourcesManagerImpl(
                    profileInfo,
                    profileDb.eventStream());
        };
    }

    @Bean
    public VmOperationManager.Factory vmOperationManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new VmOperationManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public BlockingManager.Factory blockingManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new BlockingManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public SecurityManager.Factory securityManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new SecurityManagerImpl(
                    profileInfo,
                    profileDb.eventStream());
        };
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ContainerManagerImpl(profileDb.eventStream());
        };
    }

    @Bean
    public IoManager.Factory ioManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new IoManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public AllocationManager.Factory allocationManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new AllocationManagerImpl(
                    profileInfo,
                    profileDb.events(),
                    profileDb.eventStream());
        };
    }

    /**
     * Creates the span manager backed by the real {@code profiler.Span} event repository
     * (read from the per-profile database).
     */
    @Bean
    public SpanManager.Factory spanManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            SpanRepository repository = profileDb.spans();
            return new SpanManagerImpl(repository);
        };
    }

    /**
     * Creates the trace manager over the trace tables derived from the profile's events during
     * initialization.
     */
    @Bean
    public TraceManager.Factory traceManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            TraceRepository repository = profileDb.traces();
            return new TraceManagerImpl(repository);
        };
    }

    /**
     * Creates the attribute manager over the attribute index derived from the profile's spans during
     * initialization, alongside the trace tables themselves.
     */
    @Bean
    public TraceAttributesManager.Factory traceAttributesManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            TraceAttributeRepository repository =
                    profileDb.traceAttributes();
            return new TraceAttributesManagerImpl(repository);
        };
    }
}
