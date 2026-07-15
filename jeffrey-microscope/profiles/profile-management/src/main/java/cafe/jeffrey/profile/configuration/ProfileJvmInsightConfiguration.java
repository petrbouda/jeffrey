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

package cafe.jeffrey.profile.configuration;

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.manager.*;
// Explicit import: disambiguates from java.lang.SecurityManager (both match the wildcard above)
import cafe.jeffrey.profile.manager.SecurityManager;
import cafe.jeffrey.profile.manager.registry.JvmInsightFactories;
import cafe.jeffrey.profile.thread.CachingThreadProvider;
import cafe.jeffrey.profile.thread.DbBasedThreadProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.provider.profile.api.SpanRepository;

import javax.sql.DataSource;
import java.time.Clock;

public class ProfileJvmInsightConfiguration {

    private final ProfileRepositories profileRepositories;
    private final DatabaseManagerResolver databaseManagerResolver;

    public ProfileJvmInsightConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
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
            SpanManager.Factory spanFactory) {

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
                spanFactory);
    }

    @Bean
    public GarbageCollectionManager.Factory gcManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new GarbageCollectionManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new JITCompilationManagerImpl(
                    profileInfo,
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public JITDeoptimizationManager.Factory jitDeoptimizationManager() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new JITDeoptimizationManagerImpl(
                    profileInfo,
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventRepository eventRepository = profileRepositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventStreamRepository = profileRepositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);

            return new ThreadManagerImpl(
                    profileInfo,
                    eventRepository,
                    eventStreamRepository,
                    eventTypeRepository,
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository),
                            profileRepositories.newProfileCacheRepository(profileDb)));
        };
    }

    @Bean
    public VirtualThreadManager.Factory virtualThreadManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new VirtualThreadManagerImpl(
                    profileInfo,
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new HeapMemoryManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public HeapDumpManager.Factory heapDumpManagerFactory(
            AdditionalFilesManager.Factory additionalFilesManagerFactory,
            Clock clock,
            OqlEngine oqlEngine) {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new HeapDumpManagerImpl(
                    profileInfo,
                    additionalFilesManagerFactory.apply(profileInfo),
                    profileRepositories.newEventRepository(profileDb),
                    clock,
                    oqlEngine);
        };
    }

    @Bean
    public LeakCandidatesManager.Factory leakCandidatesManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new LeakCandidatesManagerImpl(profileRepositories.newEventRepository(profileDb));
        };
    }

    @Bean
    public ClassLoadingManager.Factory classLoadingManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ClassLoadingManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ExceptionsManager.Factory exceptionsManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ExceptionsManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public NativeMemoryManager.Factory nativeMemoryManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new NativeMemoryManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public NativeMemoryTrackingManager.Factory nativeMemoryTrackingManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new NativeMemoryTrackingManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public SystemResourcesManager.Factory systemResourcesManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new SystemResourcesManagerImpl(
                    profileInfo,
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public VmOperationManager.Factory vmOperationManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new VmOperationManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public BlockingManager.Factory blockingManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new BlockingManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public SecurityManager.Factory securityManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new SecurityManagerImpl(
                    profileInfo,
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ContainerManagerImpl(profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public IoManager.Factory ioManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new IoManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public AllocationManager.Factory allocationManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new AllocationManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    /**
     * Creates the span manager backed by the real {@code profiler.Span} event repository
     * (read from the per-profile database).
     */
    @Bean
    public SpanManager.Factory spanManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            SpanRepository repository = profileRepositories.newSpanRepository(profileDb);
            return new SpanManagerImpl(repository);
        };
    }
}
