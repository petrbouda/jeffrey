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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.guardian.CachingGuardianProvider;
import cafe.jeffrey.profile.guardian.Guardian;
import cafe.jeffrey.profile.guardian.GuardianProvider;
import cafe.jeffrey.profile.guardian.ParsingGuardianProvider;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.AutoAnalysisManagerImpl;
import cafe.jeffrey.profile.manager.EventViewerManager;
import cafe.jeffrey.profile.manager.EventViewerManagerImpl;
import cafe.jeffrey.profile.manager.FlagsManager;
import cafe.jeffrey.profile.manager.FlagsManagerImpl;
import cafe.jeffrey.profile.manager.GuardianManager;
import cafe.jeffrey.profile.manager.GuardianManagerImpl;
import cafe.jeffrey.profile.manager.JvmFlagDescriptionProvider;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ProfileConfigurationManagerImpl;
import cafe.jeffrey.profile.manager.registry.AnalysisFactories;
import cafe.jeffrey.profile.settings.ActiveSettingsProvider;
import cafe.jeffrey.profile.settings.CachedActiveSettingsProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public class ProfileAnalysisConfiguration {

    private final ProfileRepositories profileRepositories;
    private final DatabaseManagerResolver databaseManagerResolver;

    public ProfileAnalysisConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
    }

    @Bean
    public AnalysisFactories analysisFactories(
            GuardianManager.Factory guardianFactory,
            AutoAnalysisManager.Factory autoAnalysisFactory,
            EventViewerManager.Factory eventViewerFactory,
            FlagsManager.Factory flagsFactory) {

        return new AnalysisFactories(
                guardianFactory,
                autoAnalysisFactory,
                eventViewerFactory,
                flagsFactory);
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            ActiveSettingsProvider.Factory settingsProviderFactory,
            GuardDefinitions guardDefinitions) {
        return (profileInfo) -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventRepository eventsRepository = profileRepositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventsStreamRepository = profileRepositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventsTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(
                    profileInfo, eventsRepository, eventsStreamRepository, eventsTypeRepository,
                    settingsProvider.get(), guardDefinitions);

            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(guardian), guardDefinitions);

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(
            RecordingStorage recordingStorage,
            @Qualifier(ProfilesConfiguration.RECORDINGS_PATH) Path recordingsPath) {

        return profileInfo -> {
            var profileDb = databaseManagerResolver.open(profileInfo);
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);

            Supplier<Optional<Path>> recordingPathResolver;
            if (profileInfo.projectId() != null) {
                recordingPathResolver = () -> recordingStorage
                        .projectRecordingStorage(profileInfo.projectId())
                        .findRecording(profileInfo.recordingId());
            } else {
                recordingPathResolver = () -> findRecording(recordingsPath, profileInfo.recordingId());
            }

            return new AutoAnalysisManagerImpl(cacheRepository, recordingPathResolver);
        };
    }

    private static Optional<Path> findRecording(Path recordingsPath, String recordingId) {
        if (recordingId == null || !Files.exists(recordingsPath)) {
            return Optional.empty();
        }
        try (var stream = Files.list(recordingsPath)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(recordingId + "-"))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new EventViewerManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public JvmFlagDescriptionProvider jvmFlagDescriptionProvider() {
        return new JvmFlagDescriptionProvider();
    }

    @Bean
    public FlagsManager.Factory flagsManager(JvmFlagDescriptionProvider descriptionProvider) {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new FlagsManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    descriptionProvider);
        };
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {

        return (ProfileInfo profileInfo) -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new CachedActiveSettingsProvider(
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ProfileConfigurationManagerImpl(profileRepositories.newEventTypeRepository(profileDb));
        };
    }
}
