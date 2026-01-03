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

package pbouda.jeffrey.provider.platform;

import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;

import java.io.Closeable;
import java.time.Clock;

/**
 * Provider for platform-level persistence operations.
 * Manages the platform database (projects, workspaces, schedulers, etc.)
 */
public interface PlatformPersistenceProvider extends Closeable {

    /**
     * Initialize the persistence provider with the given properties.
     *
     * @param properties persistence configuration properties
     * @param clock clock for time-based operations
     */
    void initialize(PersistenceProperties properties, Clock clock);

    /**
     * Run database migrations for the platform database.
     */
    void runMigrations();

    /**
     * Get the platform repositories factory.
     *
     * @return platform repositories factory
     */
    PlatformRepositories platformRepositories();

    @Override
    void close();
}
