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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DuckDBProfilePersistenceProvider implements ProfilePersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 10000;

    /**
     * Batch size for the Arrow events path. Bigger batches amortize the per-INSERT overhead
     * of the bulk columnar inserts (~40MB peak per batch with ~330B JSON fields per event).
     */
    private static final int ARROW_EVENTS_BATCH_SIZE = 100_000;

    private static final String ARROW_EVENTS_FLUSH_THREAD_PREFIX = "arrow-events-writer";

    private final int batchSize;
    private final DatabaseManager databaseManager;
    private final FrameResolutionMode frameResolutionMode;
    private final ExecutorService arrowEventsFlushExecutor;

    public DuckDBProfilePersistenceProvider(Clock clock, Path profilesDir, FrameResolutionMode frameResolutionMode) {
        this(clock, profilesDir, frameResolutionMode, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(
            Clock clock,
            Path profilesDir,
            FrameResolutionMode frameResolutionMode,
            int batchSize) {

        // The Arrow runtime is the only ingestion path for the events table — fail at
        // construction (startup/profile-init) instead of mid-parse when it is unusable.
        ArrowRuntimeSupport.ensureAvailable();

        this.batchSize = batchSize;
        this.databaseManager = new CachingDatabaseManager(new DuckDBProfileDatabaseManager(profilesDir), clock);
        this.frameResolutionMode = frameResolutionMode;
        // Bulk columnar inserts do not benefit from concurrency (measured: a single connection
        // saturates DuckDB's commit path), a dedicated single flush thread serializes the events
        // inserts while keeping them off the parser threads.
        this.arrowEventsFlushExecutor =
                Executors.newSingleThreadExecutor(Schedulers.platformThreadfactory(ARROW_EVENTS_FLUSH_THREAD_PREFIX));
    }

    @Override
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    @Override
    public EventWriter.Factory eventWriterFactory() {
        return dataSource -> new SQLEventWriter(() -> new DuckDBEventWriters(
                Schedulers.sharedDbWriter(), arrowEventsFlushExecutor, dataSource, batchSize, ARROW_EVENTS_BATCH_SIZE));
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
