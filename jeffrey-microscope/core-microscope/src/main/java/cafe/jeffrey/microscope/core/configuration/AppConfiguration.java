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
import cafe.jeffrey.profile.configuration.ProfilesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
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

    /**
     * Discovery walks the whole port range, most of it closed, so both halves are measured in
     * milliseconds: a scan that waited a second per port would take twenty.
     */
    private static final Duration IDE_DISCOVERY_CONNECT_TIMEOUT = Duration.ofMillis(100);
    private static final Duration IDE_DISCOVERY_READ_TIMEOUT = Duration.ofMillis(200);

    /**
     * An operation is addressed to a window that has already answered, so connecting is still quick;
     * what it then does — searching IntelliJ's indexes, reading a whole file — is not, and on a cold
     * index it is nowhere near a scan's budget.
     */
    private static final Duration IDE_OPERATION_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration IDE_OPERATION_READ_TIMEOUT = Duration.ofSeconds(5);

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

    /**
     * @param localCorePersistenceProvider backs the per-profile window link, so a checkout linked once
     *                                     is still linked after a restart. Only the first-party bridge
     *                                     has windows to remember; the single-URL one has nothing to
     *                                     store
     */
    @Bean
    public IdeBridge ideBridge(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            @Value("${jeffrey.microscope.ide.mode:jeffrey-plugin}") String mode,
            @Value("${jeffrey.microscope.ide.base-url:}") String baseUrl,
            @Value("${jeffrey.microscope.ide.scan.port-start:63342}") int portStart,
            @Value("${jeffrey.microscope.ide.scan.port-end:63362}") int portEnd) {
        IdeMode ideMode = IdeMode.fromProperty(mode);
        LOG.info("Configuring IDE IntelliJ Plugin: mode={}", ideMode.propertyValue());
        return switch (ideMode) {
            case JEFFREY_PLUGIN -> new JeffreyPluginBridge(
                    new PortRange(portStart, portEnd),
                    new JeffreyPluginClient(ideDiscoveryClientBuilder(), ideOperationsClientBuilder()),
                    new IdeTargetCache(
                            localCorePersistenceProvider.localCoreRepositories().ideTargetsRepository()));
            case JFR_PROFILER_PLUGIN -> new JfrProfilerPluginBridge(baseUrl, ideOperationsClientBuilder());
        };
    }

    /**
     * Builder for the port scan, with timeouts short enough that a closed port costs almost nothing.
     * Returning a {@link RestClient.Builder} (rather than a built client) lets tests bind a
     * {@code MockRestServiceServer} to the same builder.
     */
    private static RestClient.Builder ideDiscoveryClientBuilder() {
        return ideClientBuilder(IDE_DISCOVERY_CONNECT_TIMEOUT, IDE_DISCOVERY_READ_TIMEOUT);
    }

    /**
     * Builder for the calls that ask a window to do something. Separate from the scan's because a
     * resolve on a cold index, or a file handed over whole, does not fit in a scan's budget — and
     * shared, the timeout turned that into "the IDE window is no longer open".
     */
    private static RestClient.Builder ideOperationsClientBuilder() {
        return ideClientBuilder(IDE_OPERATION_CONNECT_TIMEOUT, IDE_OPERATION_READ_TIMEOUT);
    }

    private static RestClient.Builder ideClientBuilder(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
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

    @Bean(ProfilesConfiguration.PROFILES_PATH)
    public Path profilesPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean(ProfilesConfiguration.RECORDINGS_PATH)
    public Path recordingsPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.recordings();
    }

    @Bean
    public RecordingStorage projectRecordingStorage(MicroscopeJeffreyDirs jeffreyDirs) {
        return new FilesystemRecordingStorage(
                jeffreyDirs.recordings(),
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR,
                        SupportedRecordingFile.PPROF, SupportedRecordingFile.OTLP_PROFILE));
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
                new FileTypeDispatchingRecordingInformationParser(new JfrRecordingInformationParser(jeffreyDirs)));
    }
}
