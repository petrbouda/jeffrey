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

package pbouda.jeffrey.tests.manual.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.reader.jfr.JfrProfileInitializerProvider;
import pbouda.jeffrey.provider.writer.sqlite.DataSourceUtils;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IngestionTest {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionTest.class);

    private static final Path JEFFREY_TESTS = Path.of("/tmp/jeffrey-tests");
    private static final Path DATABASE_FILE = JEFFREY_TESTS.resolve("jeffrey-data.db");
    private static final Path RECORDINGS_TEMP_FOLDER = JEFFREY_TESTS.resolve("data");
    private static final Path RECORDING_FILE = Path.of("manual-tests/jeffrey-persons-direct-serde-cpu.jfr");

    public static void main(String[] args) throws IOException {
        try {
            if (!Files.exists(RECORDINGS_TEMP_FOLDER)) {
                Files.createDirectories(RECORDINGS_TEMP_FOLDER);
            }
            execute();
        } finally {
            FileSystemUtils.removeDirectory(JEFFREY_TESTS);
        }
    }

    public static void execute() {
        Map<String, String> writerProperties = Map.of(
                "writer.batch-size", "10000",
                "writer.url", "jdbc:sqlite:" + DATABASE_FILE,
                "writer.busy-timeout-ms", "30000",
                "writer.pool-size", "25");

        Map<String, String> readerProperties = Map.of(
                "temp-folder", RECORDINGS_TEMP_FOLDER.toString(),
                "event-fields-setting", "MANDATORY",
                "keep-source-files", "false",
                "tool.jfr.enabled", "true");

        SQLitePersistenceProvider persistenceProvider = new SQLitePersistenceProvider();
        Runtime.getRuntime().addShutdownHook(new Thread(persistenceProvider::close));
        persistenceProvider.initialize(writerProperties);
        persistenceProvider.runMigrations();

        JfrProfileInitializerProvider initializerProvider = new JfrProfileInitializerProvider();
        initializerProvider.initialize(readerProperties, persistenceProvider::newWriter);
        ProfileInitializer profileInitializer = initializerProvider.newProfileInitializer();

        profileInitializer.newProfile("project-id", RECORDING_FILE);

        DataSource dataSource = DataSourceUtils.notPool(writerProperties);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Map<String, Long> samples = DBQueries.samplesByType(jdbcTemplate);
        long total = DBQueries.samplesTotal(jdbcTemplate, "jdk.ExecutionSample");
        System.out.println();
    }
}
