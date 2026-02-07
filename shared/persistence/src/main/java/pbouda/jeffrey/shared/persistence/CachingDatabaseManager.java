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

package pbouda.jeffrey.shared.persistence;

import pbouda.jeffrey.shared.common.Schedulers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

public class CachingDatabaseManager implements DatabaseManager {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(1);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(5);

    private final DatabaseManager delegate;
    private final Clock clock;
    private final EvictableCache<String, CachedDataSource> cache;

    public CachingDatabaseManager(DatabaseManager delegate, Clock clock) {
        this(delegate, clock, DEFAULT_EXPIRATION, DEFAULT_CHECK_INTERVAL, Schedulers.sharedSingleScheduled());
    }

    public CachingDatabaseManager(DatabaseManager delegate, Clock clock, Duration expiration, Duration checkInterval) {
        this(delegate, clock, expiration, checkInterval, Schedulers.sharedSingleScheduled());
    }

    public CachingDatabaseManager(
            DatabaseManager delegate,
            Clock clock,
            Duration expiration,
            Duration checkInterval,
            ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.clock = clock;
        this.cache = new EvictableCache<>(
                value -> value.lastAccessed().plus(expiration).isBefore(clock.instant()),
                (__, value) -> value.forceClose(),
                checkInterval,
                scheduler);
    }

    @Override
    public DataSource open(String databaseUri) {
        return cache.computeIfAbsent(databaseUri, uri -> new CachedDataSource(delegate.open(uri), clock));
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        delegate.runMigrations(dataSource);
    }
}
