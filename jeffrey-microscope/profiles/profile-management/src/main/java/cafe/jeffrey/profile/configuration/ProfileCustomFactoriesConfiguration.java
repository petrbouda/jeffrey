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

package cafe.jeffrey.profile.configuration;

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.manager.custom.*;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.Type;

import javax.sql.DataSource;

public class ProfileCustomFactoriesConfiguration {

    private final ProfileRepositories repositories;
    private final DatabaseManagerResolver databaseManagerResolver;

    public ProfileCustomFactoriesConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.repositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
    }

    @Bean
    public JdbcPoolManager.Factory jdbcPoolManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new JdbcPoolManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public JdbcStatementManager.Factory jdbcStatementManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new JdbcStatementManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public HttpManager.Factory httpManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new HttpManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }

    @Bean
    public GrpcManager.Factory grpcServerManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new GrpcManagerImpl(
                    profileInfo,
                    repositories.newEventStreamRepository(dataSource),
                    Type.GRPC_SERVER_EXCHANGE);
        };
    }

    @Bean
    public MethodTracingManager.Factory methodTracingManagerFactory() {
        return profileInfo -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new MethodTracingManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(dataSource));
        };
    }
}
