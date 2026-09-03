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
import cafe.jeffrey.microscope.runtime.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.runtime.MicroscopeRuntimeConfiguration;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.ProfilesManagerImpl;
import cafe.jeffrey.microscope.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.configuration.ProfileEngineConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.parser.FileTypeDispatchingRecordingInformationParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.microscope.core.manager.GitHubReleaseChecker;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeMode;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetCache;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginBridge;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient;
import cafe.jeffrey.microscope.core.manager.ide.JfrProfilerPluginBridge;
import cafe.jeffrey.microscope.core.manager.ide.PortRange;
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
@Import({MicroscopeRuntimeConfiguration.class, ProfileEngineConfiguration.class, AiFeaturesConfiguration.class})
public class AppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfiguration.class);

    private static final Duration IDE_CLIENT_CONNECT_TIMEOUT = Duration.ofMillis(100);
    private static final Duration IDE_CLIENT_READ_TIMEOUT = Duration.ofMillis(200);

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

    @Bean(ProfileEngineConfiguration.PROFILES_PATH)
    public Path profilesPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean(ProfileEngineConfiguration.RECORDINGS_PATH)
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
