/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.GrpcManager;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.profile.manager.custom.MethodTracingManager;

public class ProfileCustomManagerImpl implements ProfileCustomManager {

    private final ProfileManager parent;
    private final JdbcPoolManager.Factory jdbcPoolManagerFactory;
    private final JdbcStatementManager.Factory jdbcStatementManagerFactory;
    private final HttpManager.Factory httpManagerFactory;
    private final GrpcManager.Factory grpcManagerFactory;
    private final MethodTracingManager.Factory methodTracingManagerFactory;

    public ProfileCustomManagerImpl(
            ProfileManager parent,
            JdbcPoolManager.Factory jdbcPoolManagerFactory,
            JdbcStatementManager.Factory jdbcStatementManagerFactory,
            HttpManager.Factory httpManagerFactory,
            GrpcManager.Factory grpcManagerFactory,
            MethodTracingManager.Factory methodTracingManagerFactory) {

        this.parent = parent;
        this.jdbcPoolManagerFactory = jdbcPoolManagerFactory;
        this.jdbcStatementManagerFactory = jdbcStatementManagerFactory;
        this.httpManagerFactory = httpManagerFactory;
        this.grpcManagerFactory = grpcManagerFactory;
        this.methodTracingManagerFactory = methodTracingManagerFactory;
    }

    @Override
    public ProfileManager parent() {
        return parent;
    }

    @Override
    public JdbcPoolManager jdbcPoolManager() {
        return jdbcPoolManagerFactory.apply(parent.info());
    }

    @Override
    public JdbcStatementManager jdbcStatementManager() {
        return jdbcStatementManagerFactory.apply(parent.info());
    }

    @Override
    public HttpManager httpManager(ExchangeDirection direction) {
        return httpManagerFactory.apply(parent.info(), direction);
    }

    @Override
    public GrpcManager grpcManager(ExchangeDirection direction) {
        return grpcManagerFactory.apply(parent.info(), direction);
    }

    @Override
    public MethodTracingManager methodTracingManager() {
        return methodTracingManagerFactory.apply(parent.info());
    }
}
