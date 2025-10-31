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
import pbouda.jeffrey.provider.api.*;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.writer.sql.SQLEventWriter;
import pbouda.jeffrey.provider.writer.sql.SQLPersistenceProvider;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ClickHousePersistenceProvider implements PersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 3000;
    private static final String DATABASE_NAME = "clickhouse";

    private final SQLPersistenceProvider corePersistenceProvider = new SQLitePersistenceProvider();

    private ClickHouseClient clickHouseClient;
    private Function<String, EventWriter> eventWriterFactory;

    @Override
    public void initialize(
            PersistenceProperties properties,
            RecordingStorage recordingStorage,
            Supplier<RecordingEventParser> recordingEventParser,
            Clock clock) {

        int batchSize = Config.parseInt(properties.events(), "batch-size", DEFAULT_BATCH_SIZE);
        String clickhouseUri = Config.parseString(properties.events(), "url");

        // Initialize data storage for Core Services (Management of Workspaces, Projects, Profiles, ...)
        corePersistenceProvider.initialize(properties, recordingStorage, recordingEventParser, clock);

        this.clickHouseClient = new ClickHouseClient(clickhouseUri);
        this.eventWriterFactory = profileId -> {
            return new SQLEventWriter(() -> new ClickHouseEventWriters(clickHouseClient, profileId, batchSize));
        };
    }

    @Override
    public void runMigrations() {
        corePersistenceProvider.runMigrations();

        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:clickhouse://localhost:8123/default",
                        "default",
                        ""
                ).validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration/" + DATABASE_NAME)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    @Override
    public ProfileInitializer.Factory newProfileInitializerFactory() {
        return corePersistenceProvider.newProfileInitializerFactory(eventWriterFactory);
    }

    @Override
    public Repositories repositories() {
        return corePersistenceProvider.repositories();
    }

    @Override
    public void close() {
        corePersistenceProvider.close();
        clickHouseClient.close();
    }
}
