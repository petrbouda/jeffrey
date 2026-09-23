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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.configuration.properties.WorkspacesProperties;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.HubRepositoryManager;
import cafe.jeffrey.hub.core.manager.project.HubProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.project.repository.FilesystemRepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.HubPersistenceProvider;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.jdbc.DuckDBHubPersistenceProvider;
import cafe.jeffrey.shared.common.StringUtils;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to HUB mode: scheduling and reconciliation.
 */
@Configuration
@Import({
        SchedulerConfiguration.class,
        ReconciliationConfiguration.class,
        HeartbeatConfiguration.class,
        VersionFeatureConfiguration.class
})
@EnableConfigurationProperties({
        SchedulerJobsProperties.class,
        DefaultWorkspaceProperties.class,
        WorkspacesProperties.class
})
@PropertySource("classpath:scheduler-defaults.properties")
public class HubAppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HubAppConfiguration.class);

    private static final String DUCKDB_URL_PREFIX = "jdbc:duckdb:";
    /** Also the file {@code HubStorageManager} measures for the storage dashboard. */
    private static final String DATABASE_FILE_NAME = "jeffrey-data.db";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HubPersistenceProvider hubPersistenceProvider(
            HubJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.hub.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? DUCKDB_URL_PREFIX + jeffreyDirs.homeDir().resolve(DATABASE_FILE_NAME)
                : databaseUrl;

        return new DuckDBHubPersistenceProvider(resolvedUrl, clock);
    }

    @Bean
    public HubPlatformRepositories platformRepositories(HubPersistenceProvider hubPersistenceProvider) {
        return hubPersistenceProvider.hubPlatformRepositories();
    }

    @Bean
    public DatabaseClientProvider databaseClientProvider(HubPersistenceProvider hubPersistenceProvider) {
        return hubPersistenceProvider.databaseClientProvider();
    }

    /**
     * Shared transaction boundary over the single hub DataSource. Repositories built from
     * {@link DatabaseClientProvider} acquire connections through Spring's
     * {@code DataSourceUtils}, so their statements participate in transactions started here.
     */
    @Bean
    public TransactionOperations hubTransactionOperations(DatabaseClientProvider databaseClientProvider) {
        return new TransactionTemplate(new DataSourceTransactionManager(databaseClientProvider.dataSource()));
    }

    @Bean
    public HubJeffreyDirs jeffreyDir(
            @Value("${jeffrey.hub.home.dir:${user.home}/.jeffrey-hub}") String homeDir,
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
        return projectInfo -> new FilesystemRepositoryStorage(
                projectInfo,
                jeffreyDirs.workspaces(),
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()));
    }

    @Bean
    public ProjectManager.Factory projectManagerFactory(
            RepositoryStorage.Factory repositoryStorageFactory,
            RepositoryManager.Factory repositoryManagerFactory,
            HubPlatformRepositories platformRepositories,
            TransactionOperations hubTransactionOperations) {
        return projectInfo -> new HubProjectManager(
                projectInfo,
                platformRepositories,
                repositoryStorageFactory.apply(projectInfo),
                repositoryManagerFactory,
                hubTransactionOperations);
    }

    @Bean
    public RepositoryManager.Factory repositoryManagerFactory(
            Clock applicationClock,
            RepositoryStorage.Factory repositoryStorageFactory,
            HubPlatformRepositories platformRepositories,
            TransactionOperations hubTransactionOperations) {
        return projectInfo -> new HubRepositoryManager(
                applicationClock,
                projectInfo,
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                platformRepositories.newProjectInstanceRepository(projectInfo.id()),
                repositoryStorageFactory.apply(projectInfo),
                hubTransactionOperations);
    }

}
