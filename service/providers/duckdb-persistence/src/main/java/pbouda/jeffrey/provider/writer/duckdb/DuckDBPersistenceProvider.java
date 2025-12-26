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

package pbouda.jeffrey.provider.writer.duckdb;

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.provider.api.*;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.writer.sql.JdbcRepositories;
import pbouda.jeffrey.provider.writer.sql.SQLEventWriter;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;
import pbouda.jeffrey.provider.writer.sql.query.SimpleComplexQueries;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolverImpl;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.function.Function;

public class DuckDBPersistenceProvider implements PersistenceProvider {

    private static final String DATABASE_NAME = "duckdb";

    private static final int DEFAULT_BATCH_SIZE = 3000;

    private final DataSourceProvider dataSourceProvider = new DuckDBDataSourceProvider();
    private final DuckDBSQLFormatter sqlFormatter = new DuckDBSQLFormatter();

    private DatabaseClientProvider databaseClientProvider;
    private DataSource dataSource;
    private Function<String, EventWriter> eventWriterFactory;
    private Clock clock;

    @Override
    public void initialize(PersistenceProperties properties, Clock clock) {
        this.clock = clock;
        int batchSize = Config.parseInt(properties.database(), "batch-size", DEFAULT_BATCH_SIZE);

        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();

        dataSource = dataSourceProvider.database(properties.database());
        this.databaseClientProvider = new DatabaseClientProvider(dataSource, false);

        this.eventWriterFactory = profileId -> {
            return new SQLEventWriter(profileId, () -> new DuckDBEventWriters(dataSource, profileId, batchSize));
        };
    }

    @Override
    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration/" + DATABASE_NAME)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    @Override
    public EventWriter.Factory newEventWriterFactory() {
        return eventWriterFactory::apply;
    }

    @Override
    public Repositories repositories() {
        ComplexQueries defaultComplexQueries = new SimpleComplexQueries(
                DuckDBFlamegraphQueries.of(),
                DuckDBTimeseriesQueries.of(),
                DuckDBSubSecondQueries.of());

        ComplexQueries nativeComplexQueries = new SimpleComplexQueries(
                new DuckDBNativeFlamegraphQueries(),
                new DuckDBNativeTimeseriesQueries(),
                new DuckDBNativeSubSecondQueries());

        QueryBuilderFactoryResolver queryBuilderFactoryResolver = new QueryBuilderFactoryResolverImpl(
                sqlFormatter, defaultComplexQueries, nativeComplexQueries);

        return new JdbcRepositories(
                sqlFormatter, queryBuilderFactoryResolver, databaseClientProvider, clock);
    }

    @Override
    public void close() {
        DataSourceUtils.close(this.dataSource);
    }
}
