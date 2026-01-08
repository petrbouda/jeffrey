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

package pbouda.jeffrey.jmh;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.parser.JfrRecordingEventParser;
import pbouda.jeffrey.provider.profile.DuckDBEventWriters;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.writer.SQLEventWriter;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.persistence.DataSourceParams;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.shared.persistence.DuckDBDataSourceProvider;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

/**
 * Initializes a DuckDB database from a JFR file for JMH benchmarks.
 * <p>
 * This utility parses a compressed JFR file and stores the events into a DuckDB database
 * that can be used by JMH benchmarks without needing to include the database in the Git repository.
 * <p>
 * Run from the project root directory:
 * <pre>
 * mvn -pl jmh-tests exec:java -Dexec.mainClass="pbouda.jeffrey.jmh.JmhDatabaseInitializer"
 * </pre>
 * Or via IDE by running the main method.
 */
public class JmhDatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(JmhDatabaseInitializer.class);

    private static final Path BASE_DIR = Path.of("jmh-tests");
    private static final Path JFR_FILE = BASE_DIR.resolve("jfr/jeffrey-persons-direct-serde-cpu.jfr.lz4");
    private static final Path DATA_DIR = BASE_DIR.resolve("data");
    private static final Path DB_FILE = DATA_DIR.resolve("profile-data.db");
    private static final Path TEMP_DIR = BASE_DIR.resolve("temp");

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private static final int BATCH_SIZE = 10000;

    private final Clock clock;

    public JmhDatabaseInitializer(Clock clock) {
        this.clock = clock;
    }

    public static void main(String[] args) {
        JmhDatabaseInitializer initializer = new JmhDatabaseInitializer(Clock.systemUTC());
        initializer.initialize();
    }

    public void initialize() {
        Instant startedAt = clock.instant();

        if (!Files.exists(JFR_FILE)) {
            LOG.error("JFR file not found: path={}", JFR_FILE.toAbsolutePath());
            System.exit(1);
        }

        if (Files.exists(DB_FILE)) {
            LOG.info("Database already exists, deleting: path={}", DB_FILE.toAbsolutePath());
            FileSystemUtils.removeFile(DB_FILE);
        }

        JeffreyDirs jeffreyDirs = new JeffreyDirs(DATA_DIR, TEMP_DIR);
        jeffreyDirs.initialize();

        DataSource dataSource = createDataSource(DB_FILE);
        try {
            runMigrations(dataSource);
            LOG.info("Database migrations completed: path={}", DB_FILE.toAbsolutePath());

            Lz4Compressor lz4Compressor = new Lz4Compressor(jeffreyDirs);
            JfrRecordingEventParser parser = new JfrRecordingEventParser(jeffreyDirs, lz4Compressor);
            EventWriter eventWriter = new SQLEventWriter(() -> new DuckDBEventWriters(dataSource, BATCH_SIZE));

            LOG.info("Parsing JFR file: path={}", JFR_FILE.toAbsolutePath());
            parser.start(eventWriter, JFR_FILE);
            eventWriter.onComplete();

            long elapsedMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli();
            LOG.info("Database initialized successfully: path={} elapsed_ms={}", DB_FILE.toAbsolutePath(), elapsedMs);
        } catch (Exception e) {
            LOG.error("Failed to initialize database: error={}", e.getMessage(), e);
            FileSystemUtils.removeFile(DB_FILE);
            throw e;
        } finally {
            DataSourceUtils.close(dataSource);
            FileSystemUtils.removeDirectory(TEMP_DIR);
        }
    }

    private static DataSource createDataSource(Path dbPath) {
        String url = "jdbc:duckdb:" + dbPath.toAbsolutePath();
        DataSourceParams params = DataSourceParams.builder()
                .url(url)
                .poolName("jmh-database-init")
                .maxPoolSize(10)
                .build();
        return DuckDBDataSourceProvider.open(params);
    }

    private static void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();
        flyway.migrate();
    }
}
