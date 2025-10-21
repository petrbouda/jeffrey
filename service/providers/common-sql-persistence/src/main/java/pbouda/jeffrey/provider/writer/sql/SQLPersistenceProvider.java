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
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class SQLPersistenceProvider implements PersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 3000;

    private final String databaseName;
    private final SQLFormatter sqlFormatter;
    private final Function<Map<String, String>, DataSource> dataSourceProvider;

    private DataSource datasource;
    private Function<String, EventWriter> eventWriterFactory;
    private EventFieldsSetting eventFieldsSetting;
    private RecordingStorage recordingStorage;
    private Supplier<RecordingEventParser> recordingEventParser;
    private Clock clock;

    public SQLPersistenceProvider(
            String databaseName,
            SQLFormatter sqlFormatter,
            Function<Map<String, String>, DataSource> dataSourceProvider) {
        this.databaseName = databaseName;
        this.sqlFormatter = sqlFormatter;
        this.dataSourceProvider = dataSourceProvider;
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

        this.datasource = dataSourceProvider.apply(properties);
        this.eventWriterFactory = profileId -> new SQLEventWriter(profileId, datasource, batchSize, clock);
    }

    @Override
    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(this.datasource)
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
                datasource,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                recordingEventParser.get(),
                eventWriterFactory,
                eventFieldsSetting,
                clock);
    }

    @Override
    public Repositories repositories() {
        return new JdbcRepositories(sqlFormatter, datasource, clock);
    }

    @Override
    public void close() {
        if (datasource != null && datasource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new RuntimeException("Cannot release the datasource to the database", e);
            }
        }
    }
}
