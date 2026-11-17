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
import cafe.jeffrey.provider.profile.api.ProfileDatabase;
import cafe.jeffrey.provider.profile.api.ProfileDatabases;


public class ProfileCustomFactoriesConfiguration {

    private final ProfileDatabases profileDatabases;

    public ProfileCustomFactoriesConfiguration(
            ProfileDatabases profileDatabases) {
        this.profileDatabases = profileDatabases;
    }

    @Bean
    public JdbcPoolManager.Factory jdbcPoolManagerFactory() {
        return profileInfo -> {
            ProfileDatabase dataSource = profileDatabases.open(profileInfo);
            return new JdbcPoolManagerImpl(
                    profileInfo, dataSource.eventStream());
        };
    }

    @Bean
    public JdbcStatementManager.Factory jdbcStatementManagerFactory() {
        return profileInfo -> {
            ProfileDatabase dataSource = profileDatabases.open(profileInfo);
            return new JdbcStatementManagerImpl(
                    profileInfo, dataSource.eventStream());
        };
    }

    @Bean
    public HttpManager.Factory httpManagerFactory() {
        return (profileInfo, direction) -> {
            ProfileDatabase dataSource = profileDatabases.open(profileInfo);
            return new HttpManagerImpl(
                    profileInfo,
                    dataSource.eventStream(),
                    direction.httpEventType());
        };
    }

    @Bean
    public GrpcManager.Factory grpcManagerFactory() {
        return (profileInfo, direction) -> {
            ProfileDatabase dataSource = profileDatabases.open(profileInfo);
            return new GrpcManagerImpl(
                    profileInfo,
                    dataSource.eventStream(),
                    direction.grpcEventType());
        };
    }

    @Bean
    public MethodTracingManager.Factory methodTracingManagerFactory() {
        return profileInfo -> {
            ProfileDatabase dataSource = profileDatabases.open(profileInfo);
            return new MethodTracingManagerImpl(
                    profileInfo, dataSource.eventStream());
        };
    }
}
