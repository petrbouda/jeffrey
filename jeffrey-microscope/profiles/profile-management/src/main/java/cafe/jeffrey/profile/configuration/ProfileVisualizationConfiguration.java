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

package cafe.jeffrey.profile.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import cafe.jeffrey.profile.manager.DiffFlamegraphManagerImpl;
import cafe.jeffrey.profile.manager.DiffTimeseriesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.PrimaryFlamegraphManager;
import cafe.jeffrey.profile.manager.PrimaryTimeseriesManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.profile.manager.SubSecondManagerImpl;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.manager.registry.VisualizationFactories;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;

import javax.sql.DataSource;

public class ProfileVisualizationConfiguration {

    private static final String MIN_FRAME_THRESHOLD_PCT_PROPERTY =
            "${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}";

    private static final String AI_EXPORT_MIN_FRAME_THRESHOLD_PCT_PROPERTY =
            "${jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct:1.0}";

    private final ProfileRepositories profileRepositories;
    private final DatabaseManagerResolver databaseManagerResolver;

    public ProfileVisualizationConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
    }

    @Bean
    public JfrFlamegraphPanelProvider jfrFlamegraphPanelProvider() {
        return new JfrFlamegraphPanelProvider();
    }

    @Bean
    public StackSampleFlamegraphPanelProvider stackSampleFlamegraphPanelProvider() {
        return new StackSampleFlamegraphPanelProvider();
    }

    @Bean
    public VisualizationFactories visualizationFactories(
            FlamegraphManager.Factory flamegraphFactory,
            FlamegraphManager.DifferentialFactory flamegraphDiffFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            TimeseriesManager.DifferentialFactory timeseriesDiffFactory) {

        return new VisualizationFactories(
                flamegraphFactory,
                flamegraphDiffFactory,
                subSecondFactory,
                timeseriesFactory,
                timeseriesDiffFactory);
    }

    /**
     * Both thresholds are static application properties, captured once when the factory bean is built.
     * The agent-export one becomes an {@link AiExportConfig}, whose constructor rejects a value outside
     * {@code (0, 100)} at boot rather than on the first export.
     */
    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            @Value(MIN_FRAME_THRESHOLD_PCT_PROPERTY) double minFrameThresholdPct,
            @Value(AI_EXPORT_MIN_FRAME_THRESHOLD_PCT_PROPERTY) double aiExportMinFrameThresholdPct) {

        AiExportConfig aiExportConfig = new AiExportConfig(aiExportMinFrameThresholdPct);
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileEventStreamRepository eventRepository = profileRepositories.newEventStreamRepository(profileDb);
            return new PrimaryFlamegraphManager(eventTypeRepository,
                    new DbBasedFlamegraphGenerator(
                            eventRepository, minFrameThresholdPct, aiExportConfig));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            @Value(MIN_FRAME_THRESHOLD_PCT_PROPERTY) double minFrameThresholdPct,
            @Value(AI_EXPORT_MIN_FRAME_THRESHOLD_PCT_PROPERTY) double aiExportMinFrameThresholdPct) {

        AiExportConfig aiExportConfig = new AiExportConfig(aiExportMinFrameThresholdPct);
        return (primary, secondary) -> {
            DataSource primaryDb = databaseManagerResolver.open(primary);
            DataSource secondaryDb = databaseManagerResolver.open(secondary);
            return new DiffFlamegraphManagerImpl(
                    primary,
                    secondary,
                    profileRepositories.newEventTypeRepository(primaryDb),
                    profileRepositories.newEventTypeRepository(secondaryDb),
                    new DbBasedDiffgraphGenerator(
                            profileRepositories.newEventStreamRepository(primaryDb),
                            profileRepositories.newEventStreamRepository(secondaryDb),
                            minFrameThresholdPct),
                    aiExportConfig
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(profileRepositories.newEventStreamRepository(profileDb)));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory() {
        return (primary, secondary) -> {
            DataSource primaryDb = databaseManagerResolver.open(primary);
            DataSource secondaryDb = databaseManagerResolver.open(secondary);
            return new DiffTimeseriesManager(
                    primary.profilingStartEnd(),
                    secondary.profilingStartEnd(),
                    profileRepositories.newEventStreamRepository(primaryDb),
                    profileRepositories.newEventStreamRepository(secondaryDb));
        };
    }
}
