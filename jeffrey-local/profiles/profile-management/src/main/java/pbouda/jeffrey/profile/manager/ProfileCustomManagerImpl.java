/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.profile.manager.custom.HttpManager;
import pbouda.jeffrey.profile.manager.custom.JdbcPoolManager;
import pbouda.jeffrey.profile.manager.custom.JdbcStatementManager;
import pbouda.jeffrey.profile.manager.custom.MethodTracingManager;

public class ProfileCustomManagerImpl implements ProfileCustomManager {

    private final ProfileManager parent;
    private final JdbcPoolManager.Factory jdbcPoolManagerFactory;
    private final JdbcStatementManager.Factory jdbcStatementManagerFactory;
    private final HttpManager.Factory httpManagerFactory;
    private final MethodTracingManager.Factory methodTracingManagerFactory;

    public ProfileCustomManagerImpl(
            ProfileManager parent,
            JdbcPoolManager.Factory jdbcPoolManagerFactory,
            JdbcStatementManager.Factory jdbcStatementManagerFactory,
            HttpManager.Factory httpManagerFactory,
            MethodTracingManager.Factory methodTracingManagerFactory) {

        this.parent = parent;
        this.jdbcPoolManagerFactory = jdbcPoolManagerFactory;
        this.jdbcStatementManagerFactory = jdbcStatementManagerFactory;
        this.httpManagerFactory = httpManagerFactory;
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
    public HttpManager httpManager() {
        return httpManagerFactory.apply(parent.info());
    }

    @Override
    public MethodTracingManager methodTracingManager() {
        return methodTracingManagerFactory.apply(parent.info());
    }
}
