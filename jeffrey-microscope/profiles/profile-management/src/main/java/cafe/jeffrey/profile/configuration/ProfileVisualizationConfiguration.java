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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import cafe.jeffrey.profile.manager.DiffFlamegraphManagerImpl;
import cafe.jeffrey.profile.manager.DiffTimeseriesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.PrimaryFlamegraphManager;
import cafe.jeffrey.profile.manager.PrimaryTimeseriesManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.profile.manager.SubSecondManagerImpl;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.manager.registry.VisualizationFactories;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;

import javax.sql.DataSource;

public class ProfileVisualizationConfiguration {

    private final ProfileRepositories profileRepositories;
    private final DatabaseManagerResolver databaseManagerResolver;

    public ProfileVisualizationConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
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

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            @Value("${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct,
            @Value("${jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct:1.0}") double aiExportMinFrameThresholdPct) {

        AiExportConfig aiExportConfig = new AiExportConfig(aiExportMinFrameThresholdPct);
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileEventStreamRepository eventRepository = profileRepositories.newEventStreamRepository(profileDb);
            return new PrimaryFlamegraphManager(eventTypeRepository,
                    new DbBasedFlamegraphGenerator(eventRepository, minFrameThresholdPct, aiExportConfig));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            @Value("${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct) {

        return (primary, secondary) -> {
            DataSource primaryDb = databaseManagerResolver.open(primary);
            DataSource secondaryDb = databaseManagerResolver.open(secondary);
            return new DiffFlamegraphManagerImpl(
                    profileRepositories.newEventTypeRepository(primaryDb),
                    profileRepositories.newEventTypeRepository(secondaryDb),
                    new DbBasedDiffgraphGenerator(
                            profileRepositories.newEventStreamRepository(primaryDb),
                            profileRepositories.newEventStreamRepository(secondaryDb),
                            minFrameThresholdPct)
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
