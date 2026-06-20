/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst.configuration;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.shared.persistence.DataSourceParams;
import cafe.jeffrey.shared.persistence.DataSourceProvider;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens the performance-analyst SQLite database — the single persistence engine for the deployment —
 * runs its Flyway migrations, and exposes a {@link DatabaseClientProvider} for the repositories. The
 * underlying {@link DataSource} is deliberately kept as a local — it is not a Spring bean — so Spring
 * Boot's DataSource/Flyway auto-configuration is not triggered for it.
 *
 * <p>The pool is pinned to a single connection: SQLite is single-writer, so a pool size of 1 serializes
 * access and avoids internal {@code SQLITE_BUSY}. Connections open in WAL mode with the recommended
 * durability pragmas — WAL plus {@code synchronous=NORMAL} keeps commits crash-safe (the database never
 * corrupts; only a power loss can drop the most recent transaction) while skipping an fsync per commit;
 * {@code foreign_keys=true} is mandatory because the schema relies on {@code ON DELETE CASCADE} and
 * SQLite enforces foreign keys only per connection; {@code busy_timeout} waits out a held lock (e.g. a
 * WAL checkpoint) instead of failing immediately. The pragmas are passed as JDBC connection properties,
 * so the xerial driver reapplies the per-connection ones on every pooled connection;
 * {@code journal_mode=WAL} is persisted in the database header.
 */
@Configuration
public class DataSourceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceConfiguration.class);

    private static final String HOME_DIR =
            "${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}";
    private static final String DATABASE_FILE = "performance-analyst.sqlite";
    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration/performance-analyst/core";
    private static final String POOL_NAME = "performance-analyst-sqlite-pool";
    private static final int MAX_POOL_SIZE = 1;

    // SQLite pragmas applied per connection (xerial recognizes these as connection-property keys).
    private static final String PRAGMA_JOURNAL_MODE = "journal_mode";
    private static final String PRAGMA_SYNCHRONOUS = "synchronous";
    private static final String PRAGMA_FOREIGN_KEYS = "foreign_keys";
    private static final String PRAGMA_BUSY_TIMEOUT = "busy_timeout";

    private static final String JOURNAL_MODE_WAL = "WAL";
    private static final String SYNCHRONOUS_NORMAL = "NORMAL";
    private static final String FOREIGN_KEYS_ON = "true";
    private static final String BUSY_TIMEOUT_MILLIS = "5000";

    @Bean
    public DatabaseClientProvider analystDatabaseClientProvider(@Value(HOME_DIR) String homeDir) {
        Path homeDirPath = Path.of(homeDir);
        try {
            Files.createDirectories(homeDirPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create home directory: " + homeDirPath, e);
        }

        String databaseUrl = JDBC_SQLITE_PREFIX + homeDirPath.resolve(DATABASE_FILE);
        DataSource dataSource = openDataSource(databaseUrl);
        runMigrations(dataSource);
        LOG.info("Initialized performance-analyst SQLite persistence: url={}", databaseUrl);
        return new DatabaseClientProvider(dataSource);
    }

    private static DataSource openDataSource(String databaseUrl) {
        DataSourceParams params = DataSourceParams.builder()
                .url(databaseUrl)
                .poolName(POOL_NAME)
                .maxPoolSize(MAX_POOL_SIZE)
                .enableMetrics(true)
                .additionalProperty(PRAGMA_JOURNAL_MODE, JOURNAL_MODE_WAL)
                .additionalProperty(PRAGMA_SYNCHRONOUS, SYNCHRONOUS_NORMAL)
                .additionalProperty(PRAGMA_FOREIGN_KEYS, FOREIGN_KEYS_ON)
                .additionalProperty(PRAGMA_BUSY_TIMEOUT, BUSY_TIMEOUT_MILLIS)
                .build();

        return DataSourceProvider.open(params);
    }

    private static void runMigrations(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load()
                .migrate();
    }
}
