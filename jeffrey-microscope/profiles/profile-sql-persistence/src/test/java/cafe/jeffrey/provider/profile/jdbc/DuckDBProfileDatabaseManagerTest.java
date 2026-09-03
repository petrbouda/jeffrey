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

package cafe.jeffrey.provider.profile.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sandbox around a profile database.
 * <p>
 * These are not tests of a feature; they are the guard on one. The external MCP server hands a
 * caller's SQL string to this pool, and DuckDB's file-reading functions are legal inside a
 * {@code SELECT} — so if the settings this asserts are ever dropped, an unauthenticated endpoint
 * starts serving the contents of the host's filesystem, and nothing else in the build would notice.
 */
class DuckDBProfileDatabaseManagerTest {

    @TempDir
    Path baseDir;

    private DataSource dataSource;

    private DataSource profileDatabase() {
        dataSource = new DuckDBProfileDatabaseManager(baseDir).open("p-1");
        return dataSource;
    }

    @AfterEach
    void closePool() throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    /**
     * Through a prepared statement, because that is what the MCP tool uses and it is the only shape
     * that surfaces DuckDB's real message; {@code Statement.executeQuery} reports every one of these
     * as "unsuccessful or closed pending query result", which would make this assertion meaningless.
     */
    private SQLException refusedBy(DataSource dataSource, String sql) {
        return assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                rs.next();
            }
        });
    }

    @Nested
    @DisplayName("Ordinary use is unaffected")
    class OrdinaryUse {

        @Test
        @DisplayName("the database opens, migrates and answers a query")
        void opensAndMigrates() throws SQLException {
            DataSource dataSource = profileDatabase();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM events");
                 ResultSet rs = stmt.executeQuery()) {

                assertTrue(rs.next());
                assertEquals(0, rs.getLong(1), "the migrated schema is present and queryable");
            }
        }

        @Test
        @DisplayName("the settings the sandbox does not own are still applied")
        void keepsTheIngestionSettings() throws SQLException {
            DataSource dataSource = profileDatabase();

            assertEquals("false", setting(dataSource, "preserve_insertion_order"));
            assertEquals("false", setting(dataSource, "enable_external_access"));
        }

        private String setting(DataSource dataSource, String name) throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT current_setting('" + name + "')");
                 ResultSet rs = stmt.executeQuery()) {

                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    @Nested
    @DisplayName("A query cannot leave the database")
    class Sandbox {

        @Test
        @DisplayName("reading a file from the host is refused, and says why")
        void refusesToReadAHostFile() throws Exception {
            Path secret = Files.writeString(baseDir.resolve("secret.txt"), "a private key");

            SQLException e = refusedBy(profileDatabase(),
                    "SELECT content FROM read_text('" + secret.toAbsolutePath() + "')");

            assertTrue(e.getMessage().contains("disabled by configuration"),
                    "the refusal must name the configuration, not read as a missing function: "
                            + e.getMessage());
        }

        @Test
        @DisplayName("a file function reached from inside a WHERE fragment is refused too")
        void refusesAFileFunctionInASubquery() throws Exception {
            // The shape queryEvents' whereClause lands in: AND (<caller's fragment>). A scalar
            // subquery is valid there, so the boundary has to be the engine rather than the syntax.
            Path secret = Files.writeString(baseDir.resolve("secret.txt"), "a private key");

            SQLException e = refusedBy(profileDatabase(),
                    "SELECT 1 WHERE 1=1 AND ((SELECT content FROM read_text('"
                            + secret.toAbsolutePath() + "')) IS NOT NULL)");

            assertTrue(e.getMessage().contains("disabled by configuration"), e.getMessage());
        }

        @Test
        @DisplayName("enumerating the filesystem is refused")
        void refusesToEnumerateTheFilesystem() {
            SQLException e = refusedBy(profileDatabase(),
                    "SELECT file FROM glob('" + baseDir.toAbsolutePath() + "/*')");

            assertTrue(e.getMessage().contains("disabled by configuration"), e.getMessage());
        }

        @Test
        @DisplayName("attaching another database is refused")
        void refusesToAttachAnotherDatabase() {
            SQLException e = refusedBy(profileDatabase(),
                    "ATTACH '" + baseDir.resolve("elsewhere.db").toAbsolutePath() + "' AS other");

            assertTrue(e.getMessage().contains("disabled by configuration"), e.getMessage());
        }
    }
}
