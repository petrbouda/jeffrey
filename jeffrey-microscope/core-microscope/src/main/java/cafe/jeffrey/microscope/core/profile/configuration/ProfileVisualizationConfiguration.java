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

package cafe.jeffrey.microscope.core.profile.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.microscope.core.profile.flamegraph.export.AiExportConfig;
import cafe.jeffrey.microscope.core.profile.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.microscope.core.profile.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.microscope.core.profile.subsecond.api.DbBasedSubSecondGeneratorImpl;
import cafe.jeffrey.microscope.core.profile.manager.DiffFlamegraphManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.DiffTimeseriesManager;
import cafe.jeffrey.microscope.core.profile.manager.FlamegraphManager;
import cafe.jeffrey.microscope.core.profile.manager.PrimaryFlamegraphManager;
import cafe.jeffrey.microscope.core.profile.manager.PrimaryTimeseriesManager;
import cafe.jeffrey.microscope.core.profile.manager.SubSecondManager;
import cafe.jeffrey.microscope.core.profile.manager.SubSecondManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.TimeseriesManager;
import cafe.jeffrey.microscope.core.profile.manager.registry.VisualizationFactories;
import cafe.jeffrey.microscope.core.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.microscope.core.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileDatabase;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileDatabases;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileEventTypeRepository;


public class ProfileVisualizationConfiguration {

    private static final String MIN_FRAME_THRESHOLD_PCT_PROPERTY =
            "${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}";

    private static final String AI_EXPORT_MIN_FRAME_THRESHOLD_PCT_PROPERTY =
            "${jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct:2.0}";

    private final ProfileDatabases profileDatabases;

    public ProfileVisualizationConfiguration(
            ProfileDatabases profileDatabases) {
        this.profileDatabases = profileDatabases;
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
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            ProfileEventTypeRepository eventTypeRepository = profileDb.eventTypes();
            ProfileEventStreamRepository eventRepository = profileDb.eventStream();
            return new PrimaryFlamegraphManager(eventTypeRepository,
                    new DbBasedFlamegraphGenerator(
                            eventRepository, minFrameThresholdPct, aiExportConfig),
                    profileInfo.eventSource().isFlamegraphOnlyImport());
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            @Value(MIN_FRAME_THRESHOLD_PCT_PROPERTY) double minFrameThresholdPct,
            @Value(AI_EXPORT_MIN_FRAME_THRESHOLD_PCT_PROPERTY) double aiExportMinFrameThresholdPct) {

        AiExportConfig aiExportConfig = new AiExportConfig(aiExportMinFrameThresholdPct);
        return (primary, secondary) -> {
            ProfileDatabase primaryDb = profileDatabases.open(primary);
            ProfileDatabase secondaryDb = profileDatabases.open(secondary);
            return new DiffFlamegraphManagerImpl(
                    primary,
                    secondary,
                    primaryDb.eventTypes(),
                    secondaryDb.eventTypes(),
                    new DbBasedDiffgraphGenerator(
                            primaryDb.eventStream(),
                            secondaryDb.eventStream(),
                            minFrameThresholdPct),
                    aiExportConfig
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(profileDb.eventStream()));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory() {
        return (primary, secondary) -> {
            ProfileDatabase primaryDb = profileDatabases.open(primary);
            ProfileDatabase secondaryDb = profileDatabases.open(secondary);
            return new DiffTimeseriesManager(
                    primary.profilingStartEnd(),
                    secondary.profilingStartEnd(),
                    primaryDb.eventStream(),
                    secondaryDb.eventStream());
        };
    }
}
