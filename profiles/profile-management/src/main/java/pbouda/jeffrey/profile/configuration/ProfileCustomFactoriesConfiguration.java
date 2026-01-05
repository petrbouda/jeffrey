/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.profile.manager.custom.*;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;
import pbouda.jeffrey.shared.persistence.DatabaseManager;

import javax.sql.DataSource;

@Configuration
public class ProfileCustomFactoriesConfiguration {

    private final ProfileRepositories repositories;
    private final DatabaseManager databaseManager;

    public ProfileCustomFactoriesConfiguration(ProfilePersistenceProvider persistenceProvider) {
        this.repositories = persistenceProvider.repositories();
        this.databaseManager = persistenceProvider.databaseManager();
    }

    @Bean
    public JdbcPoolManager.Factory jdbcPoolManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManager.open(profileInfo.id());
            return new JdbcPoolManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public JdbcStatementManager.Factory jdbcStatementManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManager.open(profileInfo.id());
            return new JdbcStatementManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public HttpManager.Factory httpManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManager.open(profileInfo.id());
            return new HttpManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public MethodTracingManager.Factory methodTracingManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManager.open(profileInfo.id());
            return new MethodTracingManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }
}
