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

package cafe.jeffrey.microscope.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.ProfilesManagerImpl;
import cafe.jeffrey.microscope.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.microscope.core.recording.ProjectRecordingInitializerImpl;
import cafe.jeffrey.microscope.persistence.jdbc.DuckDBMicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import cafe.jeffrey.profile.configuration.ProfilesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.jdbc.DatabaseManagerResolverImpl;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.microscope.core.manager.GitHubReleaseChecker;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.StringUtils;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.storage.recording.api.RecordingStorage;
import cafe.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import tools.jackson.databind.ObjectMapper;
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
    public GitHubReleaseChecker gitHubReleaseChecker(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${jeffrey.microscope.update-check.enabled:true}") boolean enabled) {
        return new GitHubReleaseChecker(objectMapper, clock, enabled);
    }

    @Bean
    public MicroscopeCorePersistenceProvider platformPersistenceProvider(
            MicroscopeJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.microscope.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? "jdbc:duckdb:" + jeffreyDirs.homeDir().resolve("jeffrey-data.db")
                : databaseUrl;

        DuckDBMicroscopeCorePersistenceProvider provider = new DuckDBMicroscopeCorePersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public ProfilePersistenceProvider profilePersistenceProvider(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        LOG.info("Using frame resolution mode: mode={}", frameResolutionMode);
        return new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.profiles(), frameResolutionMode);
    }

    @Bean
    public DatabaseManagerResolver databaseManagerResolver(
            ProfilePersistenceProvider profilePersistenceProvider) {

        return new DatabaseManagerResolverImpl(profilePersistenceProvider.databaseManager());
    }

    @Bean
    public MicroscopeJeffreyDirs jeffreyDir(
            @Value("${jeffrey.microscope.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.microscope.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        MicroscopeJeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new MicroscopeJeffreyDirs(homeDirPath)
                : new MicroscopeJeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            Clock applicationClock,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            ProfileManager.Factory profileFactory,
            RecordingStorage recordingStorage,
            ProfileInitializer profileInitializer) {

        MicroscopeCoreRepositories localCoreRepositories = localCorePersistenceProvider.localCoreRepositories();
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
    public Path profilesPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean(ProfileFactoriesConfiguration.RECORDINGS_PATH)
    public Path recordingsPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.recordings();
    }

    @Bean
    public RecordingStorage projectRecordingStorage(MicroscopeJeffreyDirs jeffreyDirs) {
        return new FilesystemRecordingStorage(
                jeffreyDirs.recordings(), List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR));
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            MicroscopeJeffreyDirs jeffreyDirs) {

        MicroscopeCoreRepositories localCoreRepositories = localCorePersistenceProvider.localCoreRepositories();
        return projectInfo -> new ProjectRecordingInitializerImpl(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                localCoreRepositories.newRecordingRepository(projectInfo.id()),
                new JfrRecordingInformationParser(jeffreyDirs));
    }
}
