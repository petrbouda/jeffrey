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
import pbouda.jeffrey.provider.profile.ProfileDatabaseProvider;
import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;

import javax.sql.DataSource;

@Configuration
public class ProfileCustomFactoriesConfiguration {

    @Bean
    public JdbcPoolManager.Factory jdbcPoolManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource dataSource = databaseProvider.open(profileInfo.id());
            return new JdbcPoolManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public JdbcStatementManager.Factory jdbcStatementManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource dataSource = databaseProvider.open(profileInfo.id());
            return new JdbcStatementManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public HttpManager.Factory httpManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource dataSource = databaseProvider.open(profileInfo.id());
            return new HttpManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public MethodTracingManager.Factory methodTracingManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource dataSource = databaseProvider.open(profileInfo.id());
            return new MethodTracingManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }
}
