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

package pbouda.jeffrey.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.generator.flamegraph.GraphExporterImpl;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.DiffTimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.PrimaryTimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.profile.configuration.CachedProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.ParsingProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.ProfileConfigurationProvider;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachingActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.ParsingActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.CachingEventSummaryProvider;
import pbouda.jeffrey.profile.summary.EventSummaryProvider;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.ParsingThreadProvider;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.viewer.CachingEventViewerProvider;
import pbouda.jeffrey.profile.viewer.EventViewerProvider;
import pbouda.jeffrey.profile.viewer.ParsingTreeTableEventViewerProvider;
import pbouda.jeffrey.repository.DbBasedCacheRepository;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.SubSecondRepository;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class ProfileFactoriesConfiguration {

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {
        return (ProfileDirs profileDirs) -> {
            JdbcTemplate jdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            return new CachingActiveSettingsProvider(
                    new ParsingActiveSettingsProvider(profileDirs.allRecordingPaths()),
                    new DbBasedCacheRepository(jdbcTemplate));
        };
    }

    @Bean
    public EventSummaryProvider.Factory eventSummaryProviderFactory(
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (ProfileDirs profileDirs) -> {
            EventSummaryProvider eventSummaryProvider = new ParsingEventSummaryProvider(
                    settingsProviderFactory.apply(profileDirs),
                    profileDirs.allRecordingPaths());

            return new CachingEventSummaryProvider(
                    eventSummaryProvider,
                    new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs)));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            ProfileConfigurationProvider configurationProvider = new CachedProfileConfigurationProvider(
                    new ParsingProfileConfigurationProvider(profileDirs.allRecordingPaths()),
                    new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs)));

            return new ProfileConfigurationManagerImpl(configurationProvider);
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);
            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    new DbBasedCacheRepository(profileJdbcTemplate),
                    new ParsingGuardianProvider(profileDirs, new Guardian(settingsProvider)));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            HomeDirs homeDirs,
            EventSummaryProvider.Factory eventSummaryProviderFactory) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            EventSummaryProvider summaryProvider = eventSummaryProviderFactory.apply(profileDirs);
            return new PrimaryFlamegraphManager(
                    profileInfo,
                    profileDirs,
                    summaryProvider,
                    new GraphRepository(profileJdbcTemplate, GraphType.PRIMARY),
                    new FlamegraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (primary, secondary) -> {
            ProfileDirs primaryProfileDirs = homeDirs.profile(primary);
            ProfileDirs secondaryProfileDirs = homeDirs.profile(secondary);

            return new DiffgraphManagerImpl(
                    primary,
                    secondary,
                    primaryProfileDirs,
                    secondaryProfileDirs,
                    settingsProviderFactory.apply(primaryProfileDirs),
                    settingsProviderFactory.apply(secondaryProfileDirs),
                    new GraphRepository(JdbcTemplateFactory.create(primaryProfileDirs), GraphType.DIFFERENTIAL),
                    new DiffgraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            SubSecondRepository repository = new SubSecondRepository(JdbcTemplateFactory.create(profileDirs));
            return new SubSecondManagerImpl(profileInfo, profileDirs, repository, new SubSecondGeneratorImpl());
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);
            return new PrimaryTimeseriesManager(
                    profileInfo, profileDirs, new PrimaryTimeseriesGenerator(settingsProvider));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {
        return (primary, secondary) -> {
            TimeseriesGenerator timeseriesGenerator = new DiffTimeseriesGenerator(
                    settingsProviderFactory.apply(homeDirs.profile(primary)),
                    settingsProviderFactory.apply(homeDirs.profile(secondary)));

            return new DiffTimeseriesManager(
                    primary,
                    secondary,
                    homeDirs.profile(primary),
                    homeDirs.profile(secondary),
                    timeseriesGenerator);
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);

            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);

            List<Path> recordingPaths = profileDirs.allRecordingPaths();
            EventViewerProvider eventViewerProvider = new CachingEventViewerProvider(
                    new ParsingTreeTableEventViewerProvider(recordingPaths, settingsProviderFactory.apply(profileDirs)),
                    new DbBasedCacheRepository(profileJdbcTemplate));

            return new EventViewerManagerImpl(eventViewerProvider);
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(
            HomeDirs homeDirs,
            EventSummaryProvider.Factory eventSummaryProviderFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
//            ThreadInfoProvider threadProvider = new CachingThreadProvider(
//                    new ParsingThreadProvider(profileDirs.allRecordingPaths()),
//                    new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs)));

            EventSummaryProvider summaryProvider = eventSummaryProviderFactory.apply(profileDirs);

            ParsingThreadProvider threadProvider = new ParsingThreadProvider(
                    summaryProvider, profileInfo, profileDirs.allRecordingPaths());


            return new ThreadManagerImpl(threadProvider);
        };
    }
}