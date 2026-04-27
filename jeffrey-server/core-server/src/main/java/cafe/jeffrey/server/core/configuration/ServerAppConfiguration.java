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

package cafe.jeffrey.server.core.configuration;

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
import cafe.jeffrey.server.core.configuration.properties.JobProperties;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.server.core.appinitializer.CopyLibsInitializer;
import cafe.jeffrey.server.core.configuration.properties.ProjectProperties;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.core.manager.RepositoryManagerImpl;
import cafe.jeffrey.server.core.manager.SchedulerManager;
import cafe.jeffrey.server.core.manager.SchedulerManagerImpl;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ServerProjectManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.project.pipeline.AddProjectJobsStage;
import cafe.jeffrey.server.core.project.pipeline.ProjectPipelineCustomizer;
import cafe.jeffrey.server.core.project.repository.AsprofFileRepositoryStorage;
import cafe.jeffrey.server.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.project.repository.file.AsprofFileInfoProcessor;
import cafe.jeffrey.server.core.project.template.ProjectTemplatesLoader;
import cafe.jeffrey.server.core.scheduler.JobDefinitionLoader;
import cafe.jeffrey.server.core.scheduler.SchedulerTrigger;
import cafe.jeffrey.server.core.streaming.LiveStreamingManager;
import cafe.jeffrey.server.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.server.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.server.core.web.WebInfrastructureConfig;
import cafe.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.server.persistence.jdbc.DuckDBServerPersistenceProvider;
import cafe.jeffrey.server.persistence.ServerPersistenceProvider;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcGlobalSchedulerRepository;
import cafe.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.StringUtils;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.server.core.ServerJeffreyDirs;

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
        WebInfrastructureConfig.class
})
@EnableConfigurationProperties({ProjectProperties.class, JobProperties.class})
public class ServerAppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ServerAppConfiguration.class);

    public static final String GLOBAL_SCHEDULER_MANAGER_BEAN = "globalSchedulerManagerBean";
    public static final String PROJECTS_SYNCHRONIZER_TRIGGER = "PROJECTS_SYNCHRONIZER_TRIGGER";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ServerPersistenceProvider serverPersistenceProvider(
            ServerJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.server.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? "jdbc:duckdb:" + jeffreyDirs.homeDir().resolve("jeffrey-data.db")
                : databaseUrl;

        DuckDBServerPersistenceProvider provider = new DuckDBServerPersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public ServerPlatformRepositories platformRepositories(ServerPersistenceProvider serverPersistenceProvider) {
        return serverPersistenceProvider.serverPlatformRepositories();
    }

    @Bean
    public DatabaseClientProvider databaseClientProvider(ServerPersistenceProvider serverPersistenceProvider) {
        return serverPersistenceProvider.databaseClientProvider();
    }

    @Bean
    public ServerJeffreyDirs jeffreyDir(
            @Value("${jeffrey.server.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.server.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        ServerJeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new ServerJeffreyDirs(homeDirPath)
                : new ServerJeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean(GLOBAL_SCHEDULER_MANAGER_BEAN)
    public SchedulerManager globalSchedulerManager(DatabaseClientProvider databaseClientProvider) {
        return new SchedulerManagerImpl(new JdbcGlobalSchedulerRepository(databaseClientProvider));
    }

    @Bean
    public RepositoryStorage.Factory repositoryStorageFactory(
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories) {
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
            ServerPlatformRepositories platformRepositories,
            WorkspaceEventPublisher workspaceEventPublisher,
            InstanceEnvironmentParser instanceEnvironmentParser) {
        return projectInfo -> new ServerProjectManager(
                applicationClock,
                projectInfo,
                projectsSynchronizerTrigger,
                platformRepositories,
                repositoryStorageFactory.apply(projectInfo),
                workspaceEventPublisher,
                instanceEnvironmentParser);
    }

    @Bean
    public InstanceEnvironmentParser instanceEnvironmentParser(ServerJeffreyDirs jeffreyDirs) {
        return new InstanceEnvironmentParser(jeffreyDirs);
    }

    @Bean
    public RepositoryManager.Factory repositoryManagerFactory(
            Clock applicationClock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER)
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            RepositoryStorage.Factory repositoryStorageFactory,
            ServerPlatformRepositories platformRepositories,
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
    public ProjectTemplatesLoader projectTemplatesLoader(
            @Value("${jeffrey.server.default-project-templates:classpath:project-templates/default-project-templates.json}") String projectTemplatesPath) {
        return new ProjectTemplatesLoader(projectTemplatesPath);
    }

    @Bean
    public JobDefinitionLoader jobDefinitionLoader(
            @Value("${jeffrey.server.default-job-definitions:classpath:job-definitions/default-job-definitions.json}") String jobDefinitionsPath) {
        return new JobDefinitionLoader(jobDefinitionsPath);
    }

    @Bean
    public ProjectPipelineCustomizer addProjectJobsPipelineCustomizer(
            ServerPlatformRepositories platformRepositories,
            ProjectTemplatesLoader projectTemplatesLoader,
            JobDefinitionLoader jobDefinitionLoader) {
        return pipeline -> pipeline.addStage(
                new AddProjectJobsStage(platformRepositories, projectTemplatesLoader, jobDefinitionLoader));
    }

    @Bean
    @ConditionalOnProperty(value = "jeffrey.server.copy-libs.enabled", havingValue = "true", matchIfMissing = false)
    public CopyLibsInitializer copyLibsInitializer(
            ServerJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.server.copy-libs.source:/jeffrey-libs}") String source,
            @Value("${jeffrey.server.copy-libs.target:}") String target,
            @Value("${jeffrey.server.copy-libs.max-kept-versions:10}") int maxKeptVersions) {

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
    public JobDescriptorFactory  jobDescriptorFactory() {
        return new JobDescriptorFactory();
    }

}
