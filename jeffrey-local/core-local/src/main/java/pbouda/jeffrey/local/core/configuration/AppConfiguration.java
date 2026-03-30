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

package pbouda.jeffrey.local.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.ProfilesManagerImpl;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.local.persistence.DuckDBLocalCorePersistenceProvider;
import pbouda.jeffrey.local.persistence.LocalCorePersistenceProvider;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import pbouda.jeffrey.profile.configuration.ProfilesConfiguration;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolverImpl;
import pbouda.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.StringUtils;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;


@Configuration
@Import(ProfilesConfiguration.class)
public class AppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfiguration.class);

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public LocalCorePersistenceProvider platformPersistenceProvider(
            LocalJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.local.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? "jdbc:duckdb:" + jeffreyDirs.homeDir().resolve("jeffrey-data.db")
                : databaseUrl;

        DuckDBLocalCorePersistenceProvider provider = new DuckDBLocalCorePersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public ProfilePersistenceProvider profilePersistenceProvider(
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.local.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        LOG.info("Using frame resolution mode: mode={}", frameResolutionMode);
        return new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.profiles(), frameResolutionMode);
    }

    @Bean
    public DatabaseManagerResolver databaseManagerResolver(
            ProfilePersistenceProvider profilePersistenceProvider) {

        return new DatabaseManagerResolverImpl(profilePersistenceProvider.databaseManager());
    }

    @Bean
    public LocalJeffreyDirs jeffreyDir(
            @Value("${jeffrey.local.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.local.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        LocalJeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new LocalJeffreyDirs(homeDirPath)
                : new LocalJeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            Clock applicationClock,
            LocalCorePersistenceProvider localCorePersistenceProvider,
            ProfileManager.Factory profileFactory,
            RecordingStorage recordingStorage,
            ProfileInitializer profileInitializer) {

        LocalCoreRepositories localCoreRepositories = localCorePersistenceProvider.localCoreRepositories();
        return projectInfo ->
                new ProfilesManagerImpl(
                        applicationClock,
                        projectInfo,
                        localCoreRepositories,
                        recordingStorage.projectRecordingStorage(projectInfo.id()),
                        profileFactory,
                        profileInitializer);
    }

    @Bean(ProfileFactoriesConfiguration.PROFILES_PATH)
    public Path profilesPath(LocalJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean(ProfileFactoriesConfiguration.RECORDINGS_PATH)
    public Path recordingsPath(LocalJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.recordings();
    }

    @Bean
    public RecordingStorage projectRecordingStorage(LocalJeffreyDirs jeffreyDirs) {
        return new FilesystemRecordingStorage(
                jeffreyDirs.recordings(), List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR));
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            LocalCorePersistenceProvider localCorePersistenceProvider,
            LocalJeffreyDirs jeffreyDirs) {

        LocalCoreRepositories localCoreRepositories = localCorePersistenceProvider.localCoreRepositories();
        return projectInfo -> new ProjectRecordingInitializerImpl(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                localCoreRepositories.newRecordingRepository(projectInfo.id()),
                new JfrRecordingInformationParser(jeffreyDirs));
    }
}
