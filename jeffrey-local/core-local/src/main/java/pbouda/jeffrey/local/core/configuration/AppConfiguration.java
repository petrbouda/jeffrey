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
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.configuration.ProfilesConfiguration;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.local.persistence.DuckDBLocalCorePersistenceProvider;
import pbouda.jeffrey.local.persistence.LocalCorePersistenceProvider;
import pbouda.jeffrey.local.persistence.repository.JdbcProfilesListRepository;
import pbouda.jeffrey.local.persistence.repository.ProfilesListRepository;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolverImpl;
import pbouda.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.StringUtils;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
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
            ProfilePersistenceProvider profilePersistenceProvider,
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.local.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        ProfilePersistenceProvider quickAnalysisProvider =
                new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.quickProfiles(), frameResolutionMode);

        return new DatabaseManagerResolverImpl(
                profilePersistenceProvider.databaseManager(),
                quickAnalysisProvider.databaseManager());
    }

    @Bean
    public LocalCoreRepositories platformRepositories(LocalCorePersistenceProvider localCorePersistenceProvider) {
        return localCorePersistenceProvider.localCoreRepositories();
    }

    @Bean
    public DatabaseClientProvider databaseClientProvider(LocalCorePersistenceProvider localCorePersistenceProvider) {
        return localCorePersistenceProvider.databaseClientProvider();
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

    @Bean("profilesBaseDir")
    public Path profilesBaseDir(LocalJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean("quickProfilesBaseDir")
    public Path quickProfilesBaseDir(LocalJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.quickProfiles();
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            Clock applicationClock,
            LocalCoreRepositories localCoreRepositories,
            ProfilesListRepository profilesListRepository,
            ProfileManager.Factory profileFactory,
            RecordingStorage recordingStorage,
            ProfileInitializer profileInitializer) {

        return projectInfo ->
                new ProfilesManagerImpl(
                        applicationClock,
                        projectInfo,
                        localCoreRepositories,
                        profilesListRepository,
                        localCoreRepositories.newProjectRecordingRepository(projectInfo.id()),
                        recordingStorage.projectRecordingStorage(projectInfo.id()),
                        profileFactory,
                        profileInitializer);
    }

    @Bean
    public RecordingStorage projectRecordingStorage(
            LocalJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.local.project.recording-storage.path:}") String recordingStoragePath) {

        Path recordingsPath = recordingStoragePath.isEmpty()
                ? jeffreyDirs.homeDir().resolve("recordings")
                : Path.of(recordingStoragePath);

        if (Files.exists(recordingsPath) && !Files.isDirectory(recordingsPath)) {
            throw new IllegalArgumentException("Recordings path must be a directory");
        } else if (!Files.exists(recordingsPath)) {
            FileSystemUtils.createDirectories(recordingsPath);
        }

        List<SupportedRecordingFile> supportedTypes =
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR);

        return new FilesystemRecordingStorage(recordingsPath, supportedTypes);
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            LocalCoreRepositories localCoreRepositories,
            LocalJeffreyDirs jeffreyDirs) {

        return projectInfo -> new ProjectRecordingInitializerImpl(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                localCoreRepositories.newProjectRecordingRepository(projectInfo.id()),
                new JfrRecordingInformationParser(jeffreyDirs));
    }

    @Bean
    public ProfilesListRepository profilesListRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcProfilesListRepository(databaseClientProvider);
    }
}
