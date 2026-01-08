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

package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.query.*;
import pbouda.jeffrey.provider.profile.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.profile.query.builder.QueryBuilderFactoryResolverImpl;
import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;
import pbouda.jeffrey.provider.profile.writer.SQLEventWriter;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.persistence.CachingDatabaseManager;
import pbouda.jeffrey.shared.persistence.DatabaseManager;

import java.time.Clock;

public class DuckDBProfilePersistenceProvider implements ProfilePersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 10000;

    private final int batchSize;
    private final DatabaseManager databaseManager;
    private final FrameResolutionMode frameResolutionMode;

    public DuckDBProfilePersistenceProvider(Clock clock, JeffreyDirs jeffreyDirs) {
        this(clock, jeffreyDirs, FrameResolutionMode.CACHE, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(Clock clock, JeffreyDirs jeffreyDirs, FrameResolutionMode frameResolutionMode) {
        this(clock, jeffreyDirs, frameResolutionMode, DEFAULT_BATCH_SIZE);
    }

    public DuckDBProfilePersistenceProvider(Clock clock, JeffreyDirs jeffreyDirs, FrameResolutionMode frameResolutionMode, int batchSize) {
        this.batchSize = batchSize;
        this.databaseManager = new CachingDatabaseManager(new DuckDBProfileDatabaseManager(jeffreyDirs), clock);
        this.frameResolutionMode = frameResolutionMode;
    }

    @Override
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    @Override
    public EventWriter.Factory eventWriterFactory() {
        return dataSource -> new SQLEventWriter(() -> new DuckDBEventWriters(dataSource, batchSize));
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
