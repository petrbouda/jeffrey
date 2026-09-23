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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.provider.profile.jdbc.*;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
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
        return (dataSource, profilingStartedAt) -> {
            // One limit per profile initialization, shared by every parsing thread's writers: the
            // thing worth bounding is how much of this recording is queued for the disk at once,
            // which no single table or thread can see on its own. Sized to the writer pool, so it
            // stays saturated without ever building a backlog behind it.
            BatchFlushLimit flushLimit = BatchFlushLimit.ofSlots(Schedulers.DB_WRITER_THREADS);
            return new SQLEventWriter(() -> new DuckDBEventWriters(
                    Schedulers.sharedDbWriter(), dataSource, batchSize, profilingStartedAt, flushLimit));
        };
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
