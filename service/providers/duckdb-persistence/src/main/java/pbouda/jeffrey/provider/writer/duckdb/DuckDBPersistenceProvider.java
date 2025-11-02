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
import pbouda.jeffrey.provider.writer.sql.SQLProfileInitializer;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolverImpl;
import pbouda.jeffrey.provider.writer.sqlite.SQLiteSQLFormatter;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.function.Function;
import java.util.function.Supplier;

public class DuckDBPersistenceProvider implements PersistenceProvider {

    private static final String DATABASE_NAME = "duckdb";

    private static final int DEFAULT_BATCH_SIZE = 3000;

    private final DataSourceProvider dataSourceProvider = new DuckDBDataSourceProvider();
    private final SQLiteSQLFormatter sqlFormatter = new SQLiteSQLFormatter();

    private DatabaseClientProvider coreDatabaseClientProvider;
    private DataSource eventsDataSource;
    private Function<String, EventWriter> eventWriterFactory;
    private RecordingStorage recordingStorage;
    private Supplier<RecordingEventParser> recordingEventParser;
    private Clock clock;

    @Override
    public void initialize(
            PersistenceProperties properties,
            RecordingStorage recordingStorage,
            Supplier<RecordingEventParser> recordingEventParser,
            Clock clock) {

        this.recordingStorage = recordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.clock = clock;
        int batchSize = Config.parseInt(properties.events(), "batch-size", DEFAULT_BATCH_SIZE);

        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();


        this.coreDatabaseClientProvider = new DatabaseClientProvider(
                dataSourceProvider.core(properties.core()), false);
        this.eventsDataSource = dataSourceProvider.events(properties.events());

        this.eventWriterFactory = profileId -> {
            return new SQLEventWriter(profileId, () -> new DuckDBEventWriters(eventsDataSource, profileId, batchSize));
        };
    }

    @Override
    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(this.coreDatabaseClientProvider.dataSource())
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration/" + DATABASE_NAME)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    @Override
    public ProfileInitializer.Factory newProfileInitializerFactory() {
        return newProfileInitializerFactory(this.eventWriterFactory);
    }

    public ProfileInitializer.Factory newProfileInitializerFactory(Function<String, EventWriter> eventWriterFactory) {
        return projectInfo -> new SQLProfileInitializer(
                projectInfo,
                coreDatabaseClientProvider,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                recordingEventParser.get(),
                eventWriterFactory,
                clock);
    }

    @Override
    public Repositories repositories() {
        QueryBuilderFactoryResolver queryBuilderFactoryResolver = new QueryBuilderFactoryResolverImpl(sqlFormatter);
        return new JdbcRepositories(sqlFormatter, queryBuilderFactoryResolver, coreDatabaseClientProvider, clock);
    }

    @Override
    public void close() {
        coreDatabaseClientProvider.close();
        DataSourceUtils.close(this.eventsDataSource);
    }
}
