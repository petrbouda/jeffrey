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

package pbouda.jeffrey.provider.writer.clickhouse;

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.JdbcEventWriters;
import pbouda.jeffrey.provider.writer.sql.SQLEventWriter;
import pbouda.jeffrey.provider.writer.sql.SQLPersistenceProvider;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ClickhousePersistenceProvider implements PersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 3000;
    private static final String DATABASE_NAME = "clickhouse";

    private final PersistenceProvider corePersistenceProvider = new SQLitePersistenceProvider();

    private DatabaseClientProvider coreDatabaseClientProvider;
    private DatabaseClientProvider eventsDatabaseClientProvider;
    private Function<String, EventWriter> eventWriterFactory;
    private EventFieldsSetting eventFieldsSetting;
    private RecordingStorage recordingStorage;
    private Supplier<RecordingEventParser> recordingEventParser;
    private Clock clock;

    @Override
    public void initialize(
            Map<String, String> properties,
            RecordingStorage recordingStorage,
            Supplier<RecordingEventParser> recordingEventParser,
            Clock clock) {

        this.recordingStorage = recordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.clock = clock;
        int batchSize = Config.parseInt(properties, "events.batch-size", DEFAULT_BATCH_SIZE);
        String eventFieldsParsing = Config.parseString(properties, "event-fields-setting", "ALL");
        this.eventFieldsSetting = EventFieldsSetting.valueOf(eventFieldsParsing.toUpperCase());

        // Initialize data storage for Core Services (Management of Workspaces, Projects, Profiles, ...)
        corePersistenceProvider.initialize(properties, recordingStorage, recordingEventParser, clock);

        this.eventWriterFactory = profileId -> {
            ClickHouseDatabaseClient databaseClient = new ClickHouseDatabaseClient();
            return new SQLEventWriter(() -> new ClickHouseEventWriters(databaseClient, profileId, batchSize));
        };
    }

    @Override
    public void runMigrations() {
        corePersistenceProvider.runMigrations();

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
        return null;
    }

    @Override
    public Repositories repositories() {
        return null;
    }

    @Override
    public void close() {
        corePersistenceProvider.close();
        eventsDatabaseClientProvider.close();
    }
}
