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

package cafe.jeffrey.hub.persistence.jdbc;

import cafe.jeffrey.hub.persistence.api.HubPersistenceProvider;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.persistence.DataSourceUtils;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;
import cafe.jeffrey.shared.persistence.schema.FlywaySchemaValidator;
import cafe.jeffrey.shared.persistence.schema.SchemaValidationResult;
import org.flywaydb.core.api.exception.FlywayValidateException;

import javax.sql.DataSource;
import java.time.Clock;

public class DuckDBHubPersistenceProvider implements HubPersistenceProvider {

    private final DuckDBHubDatabaseManager databaseProvider;

    private DataSource dataSource;
    private Clock clock;

    public DuckDBHubPersistenceProvider() {
        this.databaseProvider = new DuckDBHubDatabaseManager();
    }

    @Override
    public void initialize(String databaseUrl, Clock clock) {
        this.clock = clock;

        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();

        this.dataSource = databaseProvider.open(databaseUrl);
        validateExistingSchema(databaseUrl);
        try {
            this.databaseProvider.runMigrations(dataSource);
        } catch (FlywayValidateException e) {
            failIncompatible(databaseUrl, e.getMessage());
        }
    }

    /**
     * An existing database created by an older Jeffrey version (in-place edited migration files)
     * cannot be migrated forward. Detect it before {@code migrate()} and fail with a dedicated
     * exception the hub's failure analyzer turns into an actionable console error.
     */
    private void validateExistingSchema(String databaseUrl) {
        SchemaValidationResult result = FlywaySchemaValidator.validate(
                dataSource, DuckDBHubDatabaseManager.DEFAULT_MIGRATIONS_LOCATION);
        switch (result) {
            case SchemaValidationResult.Valid ignored -> {
            }
            case SchemaValidationResult.Migratable ignored -> {
            }
            case SchemaValidationResult.Incompatible incompatible -> {
                failIncompatible(databaseUrl, String.join("; ", incompatible.details()));
            }
            case SchemaValidationResult.Unreadable unreadable -> {
                failIncompatible(databaseUrl, unreadable.message());
            }
        }
    }

    private void failIncompatible(String databaseUrl, String detail) {
        DataSourceUtils.close(dataSource);
        throw new HubDatabaseIncompatibleException(databaseUrl, detail);
    }

    @Override
    public HubPlatformRepositories serverPlatformRepositories() {
        return new JdbcHubPlatformRepositories(new DatabaseClientProvider(dataSource), clock);
    }

    @Override
    public DatabaseClientProvider databaseClientProvider() {
        return new DatabaseClientProvider(dataSource);
    }
}
