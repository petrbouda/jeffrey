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

package pbouda.jeffrey.server.core.configuration;

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
import pbouda.jeffrey.server.core.configuration.properties.JobProperties;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.shared.common.JeffreyVersion;
import pbouda.jeffrey.server.core.appinitializer.CopyLibsInitializer;
import pbouda.jeffrey.server.core.configuration.properties.ProjectProperties;
import pbouda.jeffrey.server.core.manager.SchedulerManager;
import pbouda.jeffrey.server.core.manager.SchedulerManagerImpl;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ServerProjectManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.core.project.pipeline.AddProjectJobsStage;
import pbouda.jeffrey.server.core.project.pipeline.ProjectPipelineCustomizer;
import pbouda.jeffrey.server.core.project.repository.AsprofFileRepositoryStorage;
import pbouda.jeffrey.server.core.project.repository.RepositoryStorage;
import pbouda.jeffrey.server.core.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.server.core.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.server.core.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.server.core.scheduler.SchedulerTrigger;
import pbouda.jeffrey.server.core.streaming.FileHeartbeatReader;
import pbouda.jeffrey.server.core.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.server.core.streaming.JfrStreamingInitializer;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import pbouda.jeffrey.server.persistence.DuckDBServerPersistenceProvider;
import pbouda.jeffrey.server.persistence.ServerPersistenceProvider;
import pbouda.jeffrey.server.persistence.repository.JdbcGlobalSchedulerRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.StringUtils;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to SERVER mode: scheduling, streaming, CopyLibs.
 */
@Configuration
@Import({GlobalJobsConfiguration.class, ProjectJobsConfiguration.class, JobsConfiguration.class})
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
            JfrStreamingConsumerManager jfrStreamingConsumerManager) {
        return projectInfo -> new ServerProjectManager(
                applicationClock,
                projectInfo,
                projectsSynchronizerTrigger,
                platformRepositories,
                repositoryStorageFactory.apply(projectInfo),
                workspaceEventPublisher,
                jfrStreamingConsumerManager);
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
    public JfrStreamingConsumerManager jfrStreamingConsumerManager(
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            Clock clock,
            FileHeartbeatReader fileHeartbeatReader) {

        return new JfrStreamingConsumerManager(
                jeffreyDirs, platformRepositories, clock, fileHeartbeatReader);
    }

    @Bean
    public JfrStreamingInitializer jfrStreamingInitializer(
            JfrStreamingConsumerManager jfrStreamingConsumerManager,
            WorkspacesManager workspacesManager,
            ServerPlatformRepositories platformRepositories) {

        return new JfrStreamingInitializer(jfrStreamingConsumerManager, workspacesManager, platformRepositories);
    }

    @Bean
    public JobDescriptorFactory  jobDescriptorFactory() {
        return new JobDescriptorFactory();
    }
}
