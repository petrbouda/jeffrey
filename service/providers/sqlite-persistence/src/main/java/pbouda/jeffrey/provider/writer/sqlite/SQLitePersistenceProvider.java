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

package pbouda.jeffrey.provider.writer.sqlite;

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.provider.api.*;
import pbouda.jeffrey.provider.api.repository.Repositories;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

public class SQLitePersistenceProvider implements PersistenceProvider {

    private static final int DEFAULT_BATCH_SIZE = 3000;
    private static final Path DEFAULT_RECORDINGS_FOLDER =
            Path.of(System.getProperty("java.io.tmpdir"), "jeffrey-recordings");

    private DataSource datasource;
    private Path recordingsPath;
    private Function<String, EventWriter> eventWriterFactory;
    private RecordingParserProvider parserProvider;
    private EventFieldsSetting eventFieldsSetting;

    @Override
    public void initialize(Map<String, String> properties, RecordingParserProvider parserProvider) {
        this.parserProvider = parserProvider;

        int batchSize = Config.parseInt(properties, "writer.batch-size", DEFAULT_BATCH_SIZE);
        this.recordingsPath = Config.parsePath(
                properties, "recordings.path", DEFAULT_RECORDINGS_FOLDER);
        String eventFieldsParsing = Config.parseString(properties, "event-fields-setting", "ALL");
        this.eventFieldsSetting = EventFieldsSetting.valueOf(eventFieldsParsing.toUpperCase());

        this.datasource = DataSourceUtils.pooled(properties);
        this.eventWriterFactory = profileId -> new SQLiteEventWriter(profileId, datasource, batchSize);
    }

    @Override
    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(this.datasource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration")
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    @Override
    public ProfileInitializer newProfileInitializer(String projectId) {
        return new SQLiteProfileInitializer(
                projectId,
                recordingsPath,
                datasource,
                parserProvider.newRecordingEventParser(),
                eventWriterFactory,
                eventFieldsSetting);
    }

    @Override
    public RecordingInitializer newRecordingInitializer(String projectId) {
        return new JdbcRecordingInitializer(
                projectId,
                recordingsPath,
                datasource,
                parserProvider.newRecordingInformationParser());
    }

    @Override
    public Repositories repositories() {
        return new JdbcRepositories(datasource);
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
