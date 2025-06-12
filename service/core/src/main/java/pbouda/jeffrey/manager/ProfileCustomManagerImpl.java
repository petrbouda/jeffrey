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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.manager.custom.HttpManager;
import pbouda.jeffrey.manager.custom.JdbcPoolManager;

public class ProfileCustomManagerImpl implements ProfileCustomManager {

    private final ProfileManager parent;
    private final JdbcPoolManager.Factory jdbcPoolManagerFactory;
    private final HttpManager.Factory httpManagerFactory;

    public ProfileCustomManagerImpl(
            ProfileManager parent,
            JdbcPoolManager.Factory jdbcPoolManagerFactory,
            HttpManager.Factory httpManagerFactory) {

        this.parent = parent;
        this.jdbcPoolManagerFactory = jdbcPoolManagerFactory;
        this.httpManagerFactory = httpManagerFactory;
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
    public HttpManager httpManager() {
        return httpManagerFactory.apply(parent.info());
    }
}
