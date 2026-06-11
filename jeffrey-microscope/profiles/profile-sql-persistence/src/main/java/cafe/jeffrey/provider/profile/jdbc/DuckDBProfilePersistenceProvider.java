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
import cafe.jeffrey.shared.common.EventWriterMode;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.persistence.CachingDatabaseManager;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DuckDBProfilePersistenceProvider implements ProfilePersistenceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBProfilePersistenceProvider.class);

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
    private final EventWriterMode eventWriterMode;
    private final ExecutorService arrowEventsFlushExecutor;

    public DuckDBProfilePersistenceProvider(Clock clock, Path profilesDir, FrameResolutionMode frameResolutionMode) {
        this(clock, profilesDir, frameResolutionMode, EventWriterMode.ARROW, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(
            Clock clock,
            Path profilesDir,
            FrameResolutionMode frameResolutionMode,
            EventWriterMode eventWriterMode) {

        this(clock, profilesDir, frameResolutionMode, eventWriterMode, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(
            Clock clock,
            Path profilesDir,
            FrameResolutionMode frameResolutionMode,
            EventWriterMode eventWriterMode,
            int batchSize) {

        this.batchSize = batchSize;
        this.databaseManager = new CachingDatabaseManager(new DuckDBProfileDatabaseManager(profilesDir), clock);
        this.frameResolutionMode = frameResolutionMode;
        this.eventWriterMode = resolveEventWriterMode(eventWriterMode);
        // Bulk columnar inserts do not benefit from concurrency (measured: a single connection
        // saturates DuckDB's commit path), a dedicated single flush thread serializes the events
        // inserts while keeping them off the parser threads.
        this.arrowEventsFlushExecutor = this.eventWriterMode == EventWriterMode.ARROW
                ? Executors.newSingleThreadExecutor(Schedulers.platformThreadfactory(ARROW_EVENTS_FLUSH_THREAD_PREFIX))
                : null;
    }

    @Override
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    /**
     * @return the effective writer mode for the events table — may differ from the requested
     * mode when the Arrow runtime is unavailable on the current platform
     */
    public EventWriterMode eventWriterMode() {
        return eventWriterMode;
    }

    @Override
    public EventWriter.Factory eventWriterFactory() {
        if (eventWriterMode == EventWriterMode.ARROW) {
            return dataSource -> new SQLEventWriter(() -> DuckDBEventWriters.arrowBased(
                    Schedulers.sharedDbWriter(), arrowEventsFlushExecutor, dataSource, batchSize, ARROW_EVENTS_BATCH_SIZE));
        }
        return dataSource -> new SQLEventWriter(
                () -> DuckDBEventWriters.appenderBased(Schedulers.sharedDbWriter(), dataSource, batchSize));
    }

    private static EventWriterMode resolveEventWriterMode(EventWriterMode requested) {
        if (requested == EventWriterMode.ARROW && !ArrowRuntimeSupport.isAvailable()) {
            LOG.warn("Arrow event writer requested but the Arrow runtime is unavailable, " +
                    "falling back to the appender writer: requested_mode={}", requested);
            return EventWriterMode.APPENDER;
        }
        return requested;
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
