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
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.SingleSlotDatabaseManager;

import java.nio.file.Path;

public class DuckDBProfilePersistenceProvider implements ProfilePersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 10000;

    /**
     * Batch size for the Arrow events path. Bigger batches amortize the per-INSERT overhead
     * of the bulk columnar inserts, but they also delay the first flush and pile work up at
     * {@code close()} after parsing has already finished. 25k keeps the per-INSERT overhead
     * amortized while flushing early and often enough that vector fill + INSERT overlap with
     * parsing even on small recordings (~10MB peak per batch with ~330B JSON fields per event).
     */
    private static final int ARROW_EVENTS_BATCH_SIZE = 25_000;

    private final int batchSize;
    private final DatabaseManager databaseManager;
    private final FrameResolutionMode frameResolutionMode;

    public DuckDBProfilePersistenceProvider(Path profilesDir, FrameResolutionMode frameResolutionMode) {
        this(profilesDir, frameResolutionMode, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(Path profilesDir, FrameResolutionMode frameResolutionMode, int batchSize) {
        // The Arrow runtime is the only ingestion path for the events table — fail at
        // construction (startup/profile-init) instead of mid-parse when it is unusable.
        ArrowRuntimeSupport.ensureAvailable();

        this.batchSize = batchSize;
        // Only a single profile is opened at a time: the single-slot manager eagerly closes the
        // previous profile's pool on switch, releasing its DuckDB instance deterministically
        this.databaseManager = new SingleSlotDatabaseManager(new DuckDBProfileDatabaseManager(profilesDir));
        this.frameResolutionMode = frameResolutionMode;
    }

    @Override
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    @Override
    public EventWriter.Factory eventWriterFactory() {
        // Events flushes run on the shared db-writer pool like every other writer: DuckDB
        // serializes the actual INSERT commits internally, but the Java-side vector fill of
        // multiple in-flight batches runs in parallel and overlaps with parsing.
        return (dataSource, profilingStartedAt) -> new SQLEventWriter(() -> new DuckDBEventWriters(
                Schedulers.sharedDbWriter(), dataSource, batchSize, ARROW_EVENTS_BATCH_SIZE, profilingStartedAt));
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
