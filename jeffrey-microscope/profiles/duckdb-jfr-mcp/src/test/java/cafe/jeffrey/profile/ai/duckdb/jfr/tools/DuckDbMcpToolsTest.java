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

package cafe.jeffrey.profile.ai.duckdb.jfr.tools;

import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guards on the JFR SQL tool.
 * <p>
 * The engine sandbox that actually confines these queries lives on the profile DataSource and is
 * asserted by {@code DuckDBProfileDatabaseManagerTest}. What is left here is everything the tool
 * itself owes its caller: a row cap that cannot be talked out of, and an error message worth acting
 * on.
 */
@DuckDBTest
class DuckDbMcpToolsTest {

    private static final int MAX_ROWS = 1000;

    /** More rows than the cap, so a capped answer and a complete one are distinguishable. */
    private static void seedRows(DataSource dataSource, int rows) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE big AS SELECT i FROM generate_series(1, " + rows + ") AS t(i)");
        }
    }

    private static int rowsReported(String output) {
        for (String line : output.split("\n")) {
            if (line.contains("row(s) returned")) {
                return Integer.parseInt(line.trim().split(" ")[0]);
            }
        }
        throw new AssertionError("no row count in output: " + output);
    }

    @Nested
    @DisplayName("Row cap")
    class RowCap {

        @Test
        @DisplayName("caps a query that asks for everything")
        void capsAnUnboundedQuery(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big");

            assertEquals(MAX_ROWS, rowsReported(out), out);
        }

        /*
         * The regression this file exists for. The cap used to be a string append, skipped whenever
         * the query contained the substring "limit" anywhere -- so a comment, a column alias or a
         * string literal mentioning it handed back the whole table. The cap is the driver's now, and
         * none of these three can reach it.
         */
        @Test
        @DisplayName("caps a query whose only mention of limit is a comment")
        void capsWhenLimitAppearsInAComment(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big -- no limit here");

            assertEquals(MAX_ROWS, rowsReported(out), out);
        }

        @Test
        @DisplayName("caps a query whose only mention of limit is a column alias")
        void capsWhenLimitAppearsAsAnAlias(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i AS limit_reached FROM big");

            assertEquals(MAX_ROWS, rowsReported(out), out);
        }

        @Test
        @DisplayName("says the cap was reached, so a partial answer does not read as a complete one")
        void announcesTheCap(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big");

            assertTrue(out.contains("row cap was reached"), out);
        }

        @Test
        @DisplayName("stays quiet when the answer is complete")
        void saysNothingWhenUncapped(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 10);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big");

            assertEquals(10, rowsReported(out), out);
            assertFalse(out.contains("row cap was reached"), out);
        }

        @Test
        @DisplayName("a caller's own smaller LIMIT still wins")
        void respectsACallersOwnLimit(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big LIMIT 5");

            assertEquals(5, rowsReported(out), out);
        }
    }

    @Nested
    @DisplayName("Errors a caller can act on")
    class Errors {

        /*
         * Statement.executeQuery reports a missing column, a missing table and a sandbox refusal
         * identically, as "unsuccessful or closed pending query result". A model given that cannot
         * correct itself, so it retries the same query. A prepared statement carries the real one.
         */
        @Test
        @DisplayName("an unknown column comes back named, not as a generic driver failure")
        void surfacesTheRealBinderError(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT nope FROM big");

            assertTrue(out.contains("nope"), "the caller has to be told which column: " + out);
            assertFalse(out.contains("pending query result"), out);
        }

        @Test
        @DisplayName("an unknown table comes back named")
        void surfacesTheRealCatalogError(DataSource dataSource) {
            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT * FROM no_such_table");

            assertTrue(out.contains("no_such_table"), out);
            assertFalse(out.contains("pending query result"), out);
        }
    }

    @Nested
    @DisplayName("Multiple statements")
    class MultipleStatements {

        /*
         * DuckDB runs every statement in the string and only then complains that executeQuery
         * returned no result set, so the second one has already happened. Nothing else in the tool
         * stops it: the leading keyword is still SELECT, and the engine sandbox blocks the
         * filesystem rather than DDL against this database.
         */
        @Test
        @DisplayName("a statement after a semicolon is refused before anything runs")
        void refusesStackedStatements(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big; DROP TABLE big");

            assertTrue(out.startsWith("Error:"), out);
            assertTrue(tableExists(dataSource), "the DROP must not have run");
        }

        @Test
        @DisplayName("a trailing semicolon is a terminator, not a second statement")
        void allowsATrailingSemicolon(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            String out = new DuckDbMcpTools(dataSource).executeQuery("SELECT i FROM big;  ");

            assertFalse(out.startsWith("Error:"), out);
        }

        @Test
        @DisplayName("a semicolon inside a string literal is not a separator")
        void allowsASemicolonInsideALiteral() {
            assertFalse(DuckDbMcpTools.carriesMultipleStatements(
                    "SELECT i FROM big WHERE name LIKE '%;%'"));
            assertFalse(DuckDbMcpTools.carriesMultipleStatements(
                    "SELECT 'it''s; fine' AS quoted FROM big"));
        }

        @Test
        @DisplayName("a semicolon inside a comment is not a separator")
        void allowsASemicolonInsideAComment() {
            assertFalse(DuckDbMcpTools.carriesMultipleStatements("SELECT i FROM big -- ; not a statement"));
            assertFalse(DuckDbMcpTools.carriesMultipleStatements("SELECT i /* ; still not */ FROM big"));
        }

        @Test
        @DisplayName("a separator hidden behind a literal or a comment is still found")
        void findsASeparatorAfterALiteral() {
            assertTrue(DuckDbMcpTools.carriesMultipleStatements(
                    "SELECT 'a;b' FROM big; DROP TABLE big"));
            assertTrue(DuckDbMcpTools.carriesMultipleStatements(
                    "SELECT i FROM big /* c */; DROP TABLE big"));
            assertTrue(DuckDbMcpTools.carriesMultipleStatements(
                    "SELECT i FROM big;\n-- a comment\nDROP TABLE big"));
        }

        @Test
        @DisplayName("a comment after the terminator is still only one statement")
        void allowsACommentAfterTheTerminator() {
            assertFalse(DuckDbMcpTools.carriesMultipleStatements("SELECT i FROM big; -- done"));
        }

        @Test
        @DisplayName("the WHERE fragment cannot smuggle one in either")
        void guardsTheWhereClause(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            String out = new DuckDbMcpTools(dataSource)
                    .queryEvents("jdk.ExecutionSample", 10, "1=1); DROP TABLE big; --");

            assertTrue(out.startsWith("Error:"), out);
            assertTrue(tableExists(dataSource), "the DROP must not have run");
        }

        private boolean tableExists(DataSource dataSource) throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1 FROM big LIMIT 1");
                return true;
            } catch (SQLException e) {
                return false;
            }
        }
    }

    @Nested
    @DisplayName("Statement shape")
    class StatementShape {

        @Test
        @DisplayName("refuses anything that is not a SELECT or a WITH")
        void refusesNonSelect(DataSource dataSource) {
            DuckDbMcpTools tools = new DuckDbMcpTools(dataSource);

            assertTrue(tools.executeQuery("DELETE FROM big").startsWith("Error:"));
            assertTrue(tools.executeQuery("ATTACH 'other.db' AS other").startsWith("Error:"));
            assertTrue(tools.executeQuery("COPY big TO '/tmp/out.csv'").startsWith("Error:"));
        }

        @Test
        @DisplayName("accepts a WITH, which is a read")
        void acceptsWith(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            String out = new DuckDbMcpTools(dataSource)
                    .executeQuery("WITH x AS (SELECT i FROM big) SELECT COUNT(*) FROM x");

            assertFalse(out.startsWith("Error:"), out);
        }

    }
}
