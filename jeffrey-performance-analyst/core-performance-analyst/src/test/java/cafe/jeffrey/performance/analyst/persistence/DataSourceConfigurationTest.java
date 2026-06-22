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

package cafe.jeffrey.performance.analyst.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.performance.analyst.configuration.DataSourceConfiguration;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the production SQLite wiring in {@link DataSourceConfiguration}: opening the on-disk
 * database applies WAL journaling and per-connection foreign-key enforcement. WAL is on-disk only,
 * so this exercises the real configuration (not the in-memory {@code @SQLiteTest} harness).
 */
class DataSourceConfigurationTest {

    @Test
    void walModeAndForeignKeysAreEnabled(@TempDir Path tempDir) throws Exception {
        DatabaseClientProvider clientProvider = new DataSourceConfiguration()
                .analystDatabaseClientProvider(tempDir.toString());

        try (Connection connection = clientProvider.dataSource().getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase());
            }
            try (ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
