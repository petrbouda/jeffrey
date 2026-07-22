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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.appinitializer.CopyLibsInitializer;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.ProjectProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.RepositoryManagerImpl;
import cafe.jeffrey.hub.core.manager.project.HubProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.project.repository.AsprofFileRepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.file.AsprofFileInfoProcessor;
import cafe.jeffrey.hub.core.scheduler.SchedulerTrigger;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.hub.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.hub.core.streaming.LiveStreamingManager;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.web.WebInfrastructureConfig;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.hub.persistence.api.HubPersistenceProvider;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.jdbc.DuckDBHubPersistenceProvider;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.StringUtils;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to SERVER mode: scheduling, streaming, CopyLibs.
 */
@Configuration
@Import({
        GlobalJobsConfiguration.class,
        ProjectJobsConfiguration.class,
        JobsConfiguration.class,
        WebInfrastructureConfig.class,
        VersionFeatureConfiguration.class,
        KubernetesDiscoveryConfiguration.class,
        KubernetesWebhookConfiguration.class
})
@EnableConfigurationProperties({ProjectProperties.class, SchedulerJobsProperties.class})
@PropertySource("classpath:scheduler-defaults.properties")
public class HubAppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HubAppConfiguration.class);

    public static final String GLOBAL_SCHEDULER = "GLOBAL_SCHEDULER";
    public static final String PROJECTS_SYNCHRONIZER_TRIGGER = "PROJECTS_SYNCHRONIZER_TRIGGER";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public DefaultWorkspaceProperties defaultWorkspaceProperties(
            @Value("${jeffrey.hub.default-workspace.reference-id:#{T(cafe.jeffrey.shared.common.CliConstants).DEFAULT_WORKSPACE_REF_ID}}") String referenceId,
            @Value("${jeffrey.hub.default-workspace.name:#{T(cafe.jeffrey.shared.common.CliConstants).DEFAULT_WORKSPACE_REF_ID}}}") String name) {
        return new DefaultWorkspaceProperties(referenceId, name);
    }

    @Bean
    public HubPersistenceProvider serverPersistenceProvider(
            HubJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.hub.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? "jdbc:duckdb:" + jeffreyDirs.homeDir().resolve("jeffrey-data.db")
                : databaseUrl;

        DuckDBHubPersistenceProvider provider = new DuckDBHubPersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public HubPlatformRepositories platformRepositories(HubPersistenceProvider serverPersistenceProvider) {
        return serverPersistenceProvider.serverPlatformRepositories();
    }

    @Bean
    public DatabaseClientProvider databaseClientProvider(HubPersistenceProvider serverPersistenceProvider) {
        return serverPersistenceProvider.databaseClientProvider();
    }

    @Bean
    public HubJeffreyDirs jeffreyDir(
            @Value("${jeffrey.hub.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.hub.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        HubJeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new HubJeffreyDirs(homeDirPath)
                : new HubJeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean
    public RepositoryStorage.Factory repositoryStorageFactory(
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories) {
        return projectInfo -> new AsprofFileRepositoryStorage(
                projectInfo,
                jeffreyDirs.workspaces(),
                jeffreyDirs.temp(),
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                new AsprofFileInfoProcessor());
    }

    @Bean
    public ProjectManager.Factory projectManagerFactory(
            Clock applicationClock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER)
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            RepositoryStorage.Factory repositoryStorageFactory,
            HubPlatformRepositories platformRepositories,
            WorkspaceEventPublisher workspaceEventPublisher,
            InstanceEnvironmentParser instanceEnvironmentParser) {
        return projectInfo -> new HubProjectManager(
                applicationClock,
                projectInfo,
                projectsSynchronizerTrigger,
                platformRepositories,
                repositoryStorageFactory.apply(projectInfo),
                workspaceEventPublisher,
                instanceEnvironmentParser);
    }

    @Bean
    public InstanceEnvironmentParser instanceEnvironmentParser(HubJeffreyDirs jeffreyDirs) {
        return new InstanceEnvironmentParser(jeffreyDirs);
    }

    @Bean
    public RepositoryManager.Factory repositoryManagerFactory(
            Clock applicationClock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER)
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            RepositoryStorage.Factory repositoryStorageFactory,
            HubPlatformRepositories platformRepositories,
            WorkspaceEventPublisher workspaceEventPublisher,
            InstanceEnvironmentParser instanceEnvironmentParser) {
        return projectInfo -> new RepositoryManagerImpl(
                applicationClock,
                projectInfo,
                projectsSynchronizerTrigger.getObject(),
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                repositoryStorageFactory.apply(projectInfo),
                workspaceEventPublisher,
                instanceEnvironmentParser);
    }

    @Bean
    @ConditionalOnProperty(value = "jeffrey.hub.copy-libs.enabled", havingValue = "true", matchIfMissing = false)
    public CopyLibsInitializer copyLibsInitializer(
            HubJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.hub.copy-libs.source:/jeffrey-libs}") String source,
            @Value("${jeffrey.hub.copy-libs.target:}") String target,
            @Value("${jeffrey.hub.copy-libs.max-kept-versions:10}") int maxKeptVersions) {

        String resolvedTarget = StringUtils.isNullOrBlank(target)
                ? jeffreyDirs.libs().toString()
                : target;

        String version = JeffreyVersion.resolveJeffreyVersion();
        String resolvedVersion = version.startsWith("Cannot") ? null : version;

        return new CopyLibsInitializer(Path.of(source), Path.of(resolvedTarget), resolvedVersion, maxKeptVersions);
    }

    @Bean
    public FileHeartbeatReader fileHeartbeatReader() {
        return new FileHeartbeatReader();
    }

    @Bean(destroyMethod = "close")
    public LiveStreamingManager liveStreamingManager() {
        return new LiveStreamingManager();
    }

    @Bean(destroyMethod = "close")
    public ReplayStreamingManager replayStreamingManager() {
        return new ReplayStreamingManager();
    }

    @Bean
    public JobDescriptorFactory jobDescriptorFactory() {
        return new JobDescriptorFactory();
    }
}
