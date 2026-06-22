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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.persistence.GeneratedPromptRepository;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.ProjectAiConfigurationRepository;
import cafe.jeffrey.performance.analyst.persistence.ProjectRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcGeneratedPromptRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcGeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcProjectAiConfigurationRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcProjectRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcVersionControlSystemStore;
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystemStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

/**
 * Repository beans for the performance-analyst SQLite store (projects, project AI configuration, and
 * generated prompts). They are wired from the {@link DatabaseClientProvider} opened in
 * {@link DataSourceConfiguration}; the hubs/recordings repositories are wired from the same provider in
 * {@link RemoteWorkspaceConfiguration}.
 */
@Configuration
public class PerformanceAnalystPersistenceConfiguration {

    @Bean
    public GeneratedPromptRepository generatedPromptRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcGeneratedPromptRepository(databaseClientProvider);
    }

    @Bean
    public GeneratedRecommendationRepository generatedRecommendationRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcGeneratedRecommendationRepository(databaseClientProvider);
    }

    @Bean
    public ProjectRepository projectRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcProjectRepository(databaseClientProvider);
    }

    @Bean
    public ProjectAiConfigurationRepository projectAiConfigurationRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcProjectAiConfigurationRepository(databaseClientProvider);
    }

    @Bean
    public SecretEncryptor secretEncryptor() {
        return new SecretEncryptor(new MachineFingerprint());
    }

    @Bean
    public VersionControlSystemStore versionControlSystemStore(
            DatabaseClientProvider databaseClientProvider, SecretEncryptor secretEncryptor) {
        return new JdbcVersionControlSystemStore(databaseClientProvider, secretEncryptor);
    }
}
