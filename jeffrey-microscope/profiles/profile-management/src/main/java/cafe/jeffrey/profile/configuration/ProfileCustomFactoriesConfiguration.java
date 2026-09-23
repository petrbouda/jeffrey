/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.configuration;

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.manager.custom.*;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.microscope.model.Type;

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
        return (profileInfo, direction) -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new HttpManagerImpl(
                    profileInfo,
                    repositories.newEventStreamRepository(dataSource),
                    direction.httpEventType());
        };
    }

    @Bean
    public GrpcManager.Factory grpcManagerFactory() {
        return (profileInfo, direction) -> {
            DataSource dataSource = databaseManagerResolver.open(profileInfo);
            return new GrpcManagerImpl(
                    profileInfo,
                    repositories.newEventStreamRepository(dataSource),
                    direction.grpcEventType());
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
