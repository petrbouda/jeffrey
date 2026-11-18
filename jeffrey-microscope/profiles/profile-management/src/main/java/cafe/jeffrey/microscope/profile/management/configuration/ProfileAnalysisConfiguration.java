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
import cafe.jeffrey.microscope.profile.management.manager.AutoAnalysisManager;
import cafe.jeffrey.microscope.profile.management.manager.AutoAnalysisManagerImpl;
import cafe.jeffrey.microscope.profile.management.ingest.data.AutoAnalysisDataProvider;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.microscope.profile.management.manager.EventViewerManager;
import cafe.jeffrey.microscope.profile.management.manager.EventViewerManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.FlagsManager;
import cafe.jeffrey.microscope.profile.management.manager.FlagsManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.JvmFlagDescriptionProvider;
import cafe.jeffrey.microscope.profile.management.manager.ProfileConfigurationManager;
import cafe.jeffrey.microscope.profile.management.manager.ProfileConfigurationManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.SamplerHealthManager;
import cafe.jeffrey.microscope.profile.management.manager.SamplerHealthManagerImpl;
import cafe.jeffrey.microscope.profile.management.manager.registry.AnalysisFactories;
import cafe.jeffrey.microscope.profile.management.settings.ActiveSettingsProvider;
import cafe.jeffrey.microscope.profile.management.settings.CachedActiveSettingsProvider;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileDatabase;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileDatabases;
import cafe.jeffrey.microscope.profile.persistence.api.ProfileCacheRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.profile.management.recording.RecordingFileLookup;

import java.nio.file.Path;

public class ProfileAnalysisConfiguration {

    private final ProfileDatabases profileDatabases;

    public ProfileAnalysisConfiguration(
            ProfileDatabases profileDatabases) {
        this.profileDatabases = profileDatabases;
    }

    @Bean
    public AnalysisFactories analysisFactories(
            AutoAnalysisManager.Factory autoAnalysisFactory,
            EventViewerManager.Factory eventViewerFactory,
            FlagsManager.Factory flagsFactory,
            SamplerHealthManager.Factory samplerHealthFactory) {

        return new AnalysisFactories(
                autoAnalysisFactory,
                eventViewerFactory,
                flagsFactory,
                samplerHealthFactory);
    }

    @Bean
    public RecordingFileLookup recordingFileLookup(
            MicroscopeCorePersistenceProvider corePersistenceProvider,
            @Qualifier(ProfilesConfiguration.RECORDINGS_PATH) Path recordingsPath) {

        return new RecordingFileLookup(
                corePersistenceProvider.localCoreRepositories().newRecordingRepository(), recordingsPath);
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(
            RecordingFileLookup recordingLookup, TempDirFactory tempDirFactory) {
        AutoAnalysisDataProvider ruleSet = new AutoAnalysisDataProvider(tempDirFactory);
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            ProfileCacheRepository cacheRepository = profileDb.cache();

            return new AutoAnalysisManagerImpl(
                    cacheRepository,
                    () -> recordingLookup.findJfrFiles(profileInfo.recordingId()),
                    ruleSet);
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new EventViewerManagerImpl(
                    profileDb.events(),
                    profileDb.eventTypes());
        };
    }

    @Bean
    public JvmFlagDescriptionProvider jvmFlagDescriptionProvider() {
        return new JvmFlagDescriptionProvider();
    }

    @Bean
    public FlagsManager.Factory flagsManager(JvmFlagDescriptionProvider descriptionProvider) {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new FlagsManagerImpl(
                    profileDb.events(),
                    descriptionProvider);
        };
    }

    @Bean
    public SamplerHealthManager.Factory samplerHealthManager() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new SamplerHealthManagerImpl(profileDb.events());
        };
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {

        return (ProfileInfo profileInfo) -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new CachedActiveSettingsProvider(
                    profileDb.eventTypes(),
                    profileDb.cache());
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory() {

        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ProfileConfigurationManagerImpl(profileDb.eventTypes());
        };
    }
}
