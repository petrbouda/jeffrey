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
import cafe.jeffrey.microscope.persistence.jdbc.DuckDBMicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import cafe.jeffrey.profile.configuration.ProfilesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.otlpparser.OtlpRecordingInformationParser;
import cafe.jeffrey.profile.parser.FileTypeDispatchingRecordingInformationParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.jdbc.DatabaseManagerResolverImpl;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.microscope.core.manager.GitHubReleaseChecker;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeMode;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetCache;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginBridge;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient;
import cafe.jeffrey.microscope.core.manager.ide.JfrProfilerPluginBridge;
import cafe.jeffrey.microscope.core.manager.ide.PortRange;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.StringUtils;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.storage.recording.api.RecordingStorage;
import cafe.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;


@Configuration
@Import(ProfilesConfiguration.class)
public class AppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfiguration.class);

    private static final Duration IDE_CLIENT_CONNECT_TIMEOUT = Duration.ofMillis(100);
    private static final Duration IDE_CLIENT_READ_TIMEOUT = Duration.ofMillis(200);

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
    public IdeBridge ideBridge(
            @Value("${jeffrey.microscope.ide.mode:jeffrey-plugin}") String mode,
            @Value("${jeffrey.microscope.ide.base-url:}") String baseUrl,
            @Value("${jeffrey.microscope.ide.scan.port-start:63342}") int portStart,
            @Value("${jeffrey.microscope.ide.scan.port-end:63362}") int portEnd) {
        IdeMode ideMode = IdeMode.fromProperty(mode);
        LOG.info("Configuring IDE IntelliJ Plugin: mode={}", ideMode.propertyValue());
        return switch (ideMode) {
            case JEFFREY_PLUGIN -> new JeffreyPluginBridge(
                    new PortRange(portStart, portEnd),
                    new JeffreyPluginClient(ideRestClientBuilder()),
                    new IdeTargetCache());
            case JFR_PROFILER_PLUGIN -> new JfrProfilerPluginBridge(baseUrl, ideRestClientBuilder());
        };
    }

    /**
     * Builder for the IDE-plugin REST client, pre-configured with short timeouts so scanning closed
     * ports stays fast. Returning a {@link RestClient.Builder} (rather than a built client) lets tests
     * bind a {@code MockRestServiceServer} to the same builder.
     */
    private static RestClient.Builder ideRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(IDE_CLIENT_CONNECT_TIMEOUT);
        factory.setReadTimeout(IDE_CLIENT_READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
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
            MicroscopeJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode,
            Clock clock) {

        LOG.info("Using frame resolution mode: mode={}", frameResolutionMode);
        return new DuckDBProfilePersistenceProvider(jeffreyDirs.profiles(), frameResolutionMode, clock);
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
                jeffreyDirs.recordings(),
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR, SupportedRecordingFile.OTLP_PROFILE));
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            MicroscopeJeffreyDirs jeffreyDirs) {

        MicroscopeCoreRepositories localCoreRepositories = localCorePersistenceProvider.localCoreRepositories();
        return projectInfo -> new ProjectRecordingInitializer(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                localCoreRepositories.newRecordingRepository(projectInfo.id()),
                new FileTypeDispatchingRecordingInformationParser(
                        new JfrRecordingInformationParser(jeffreyDirs),
                        new OtlpRecordingInformationParser()));
    }
}
