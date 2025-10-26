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

package pbouda.jeffrey.provider.writer.sql;

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class SQLPersistenceProvider implements PersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 3000;

    private final String databaseName;
    private final SQLFormatter sqlFormatter;
    private final Function<Map<String, String>, DataSource> dataSourceProvider;
    private final boolean walCheckpointEnabled;

    private DatabaseClientProvider databaseClientProvider;
    private Function<String, EventWriter> eventWriterFactory;
    private EventFieldsSetting eventFieldsSetting;
    private RecordingStorage recordingStorage;
    private Supplier<RecordingEventParser> recordingEventParser;
    private Clock clock;

    public SQLPersistenceProvider(
            String databaseName,
            SQLFormatter sqlFormatter,
            Function<Map<String, String>, DataSource> dataSourceProvider,
            boolean walCheckpointEnabled) {
        this.databaseName = databaseName;
        this.sqlFormatter = sqlFormatter;
        this.dataSourceProvider = dataSourceProvider;
        this.walCheckpointEnabled = walCheckpointEnabled;
    }

    @Override
    public void initialize(
            Map<String, String> properties,
            RecordingStorage recordingStorage,
            Supplier<RecordingEventParser> recordingEventParser,
            Clock clock) {

        this.recordingStorage = recordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.clock = clock;
        int batchSize = Config.parseInt(properties, "writer.batch-size", DEFAULT_BATCH_SIZE);
        String eventFieldsParsing = Config.parseString(properties, "event-fields-setting", "ALL");
        this.eventFieldsSetting = EventFieldsSetting.valueOf(eventFieldsParsing.toUpperCase());

        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();

        DataSource datasource = dataSourceProvider.apply(properties);
        this.databaseClientProvider = new DatabaseClientProvider(datasource, walCheckpointEnabled);

        this.eventWriterFactory = profileId -> {
            DatabaseClient databaseClient = databaseClientProvider.provide(GroupLabel.EVENT_WRITERS);
            return new SQLEventWriter(() -> new JdbcEventWriters(databaseClient, profileId, batchSize));
        };
    }

    @Override
    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(this.databaseClientProvider.dataSource())
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration/" + this.databaseName)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    @Override
    public ProfileInitializer.Factory newProfileInitializerFactory() {
        return projectInfo -> new SQLProfileInitializer(
                projectInfo,
                databaseClientProvider,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                recordingEventParser.get(),
                eventWriterFactory,
                eventFieldsSetting,
                clock);
    }

    @Override
    public Repositories repositories() {
        return new JdbcRepositories(sqlFormatter, databaseClientProvider, clock);
    }

    @Override
    public void close() {
        databaseClientProvider.close();
    }
}
