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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.flywaydb.core.Flyway;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.persistence.DataSourceParams;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.DuckDBDataSourceProvider;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;

public class DuckDBProfileDatabaseManager implements DatabaseManager {

    private static final String PROFILE_DB_FILENAME = "profile-data.db";
    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private static final String JDBC_URL_PREFIX = "jdbc:duckdb:";

    // DuckDB setting: without the obligation to preserve insertion order, parallel ingestion
    // (appenders, CTAS re-clustering) streams batches with less memory and no final re-ordering.
    // Safe here: consumers that need chronological events request an explicit ORDER BY
    // (EventQueryConfigurer.orderedByTime), and the table is re-clustered after parsing anyway.
    private static final String PRESERVE_INSERTION_ORDER_SETTING = "preserve_insertion_order";
    private static final String PRESERVE_INSERTION_ORDER_VALUE = "false";

    // Sized so that every DB-writer thread can flush its batch concurrently during ingestion
    // instead of blocking on the pool
    private static final int MAX_POOL_SIZE = Schedulers.DB_WRITER_THREADS;

    // A single idle connection is enough to keep the embedded DuckDB instance (and its buffer
    // cache) alive between requests; further connections are created on demand
    private static final int MIN_IDLE_CONNECTIONS = 1;

    private final Path baseDir;

    public DuckDBProfileDatabaseManager(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public DataSource open(String profileId) {
        Path profileDirectory = baseDir.resolve(profileId);
        Path dbPath = profileDirectory.resolve(PROFILE_DB_FILENAME);

        if (!dbPath.toFile().exists()) {
            FileSystemUtils.createDirectories(profileDirectory);
            DataSource dataSource = createDataSource(dbPath, profileId);
            runMigrations(dataSource);
            return dataSource;
        }

        return createDataSource(dbPath, profileId);
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(PROFILE_MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    private static DataSource createDataSource(Path dbPath, String profileId) {
        String url = JDBC_URL_PREFIX + dbPath.toAbsolutePath();
        // Connections to the embedded DuckDB are in-process: there is no server that could time
        // them out, so connection retirement (maxLifetime) and keepalive pings are pure churn
        // and both are disabled (Duration.ZERO)
        DataSourceParams params = DataSourceParams.builder()
                .url(url)
                .poolName("profile-database-pool-" + profileId)
                .maxPoolSize(MAX_POOL_SIZE)
                .minIdle(MIN_IDLE_CONNECTIONS)
                .maxLifetime(Duration.ZERO)
                .keepAliveTime(Duration.ZERO)
                .additionalProperty(PRESERVE_INSERTION_ORDER_SETTING, PRESERVE_INSERTION_ORDER_VALUE)
                .build();

        return DuckDBDataSourceProvider.open(params);
    }
}
