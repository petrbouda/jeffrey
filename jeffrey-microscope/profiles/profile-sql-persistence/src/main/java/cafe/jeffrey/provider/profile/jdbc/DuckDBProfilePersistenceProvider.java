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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.provider.profile.jdbc.*;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.persistence.CachingDatabaseManager;
import cafe.jeffrey.shared.persistence.DatabaseManager;

import java.nio.file.Path;
import java.time.Clock;

public class DuckDBProfilePersistenceProvider implements ProfilePersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 10000;

    private final int batchSize;
    private final DatabaseManager databaseManager;
    private final FrameResolutionMode frameResolutionMode;

    public DuckDBProfilePersistenceProvider(Path profilesDir, FrameResolutionMode frameResolutionMode, Clock clock) {
        this(profilesDir, frameResolutionMode, clock, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(
            Path profilesDir, FrameResolutionMode frameResolutionMode, Clock clock, int batchSize) {
        this.batchSize = batchSize;
        // Per-profile pools are cached so several profiles can be initialized and read concurrently;
        // each pool is closed only after it has been idle, never on switch — so initializing a second
        // profile cannot tear down a pool the first profile is still writing to. A running
        // initialization holds a DatabaseLease that keeps its pool from being idle-evicted mid-parse.
        this.databaseManager = new CachingDatabaseManager(new DuckDBProfileDatabaseManager(profilesDir), clock);
        this.frameResolutionMode = frameResolutionMode;
    }

    @Override
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    @Override
    public EventWriter.Factory eventWriterFactory() {
        return (dataSource, profilingStartedAt) -> new SQLEventWriter(
                () -> new DuckDBEventWriters(Schedulers.sharedDbWriter(), dataSource, batchSize, profilingStartedAt));
    }

    @Override
    public ProfileRepositories repositories() {
        ComplexQueries defaultComplexQueries = new SimpleComplexQueries(
                DuckDBFlamegraphQueries.of(),
                DuckDBTimeseriesQueries.of(),
                DuckDBSubSecondQueries.of());

        ComplexQueries nativeComplexQueries = new SimpleComplexQueries(
                new DuckDBNativeFlamegraphQueries(),
                new DuckDBNativeTimeseriesQueries(),
                new DuckDBNativeSubSecondQueries());

        DuckDBSQLFormatter sqlFormatter = new DuckDBSQLFormatter();
        QueryBuilderFactoryResolver queryBuilderFactoryResolver = new QueryBuilderFactoryResolverImpl(
                sqlFormatter, defaultComplexQueries, nativeComplexQueries);

        return new JdbcProfileRepositories(sqlFormatter, queryBuilderFactoryResolver, frameResolutionMode);
    }
}
