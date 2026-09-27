/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.McpResources;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private static final String PROFILE_ID = "p-1";

    /** More rows than the cap, so a capped answer and a complete one are distinguishable. */
    private static void seedRows(DataSource dataSource, int rows) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE big AS SELECT i FROM generate_series(1, " + rows + ") AS t(i)");
        }
    }

    private static DuckDbMcpTools tools(DataSource dataSource) {
        return new DuckDbMcpTools(dataSource, PROFILE_ID, EVERY_FAMILY);
    }

    private static JsonNode query(McpToolResult result) {
        return StructuredAnswers.markdownWithoutPage(DuckDbMcpTools.class, "executeQuery", result);
    }

    private static JsonNode events(McpToolResult result) {
        return StructuredAnswers.markdown(DuckDbMcpTools.class, "queryEvents", result);
    }

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
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

            String out = tools(dataSource).executeQuery("SELECT i FROM big").text();

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

            String out = tools(dataSource).executeQuery("SELECT i FROM big -- no limit here").text();

            assertEquals(MAX_ROWS, rowsReported(out), out);
        }

        @Test
        @DisplayName("caps a query whose only mention of limit is a column alias")
        void capsWhenLimitAppearsAsAnAlias(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = tools(dataSource).executeQuery("SELECT i AS limit_reached FROM big").text();

            assertEquals(MAX_ROWS, rowsReported(out), out);
        }

        @Test
        @DisplayName("says the cap was reached, so a partial answer does not read as a complete one")
        void announcesTheCap(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            JsonNode answer = query(tools(dataSource).executeQuery("SELECT i FROM big"));

            assertEquals("ROW_LIMIT", answer.get("truncation").asString());
            assertEquals(MAX_ROWS, answer.get("returned").asInt());
            assertEquals(MAX_ROWS, answer.get("rows").size());
            assertTrue(StructuredAnswers.guidance(answer).contains("Aggregate in SQL"), answer.toString());
        }

        @Test
        @DisplayName("stays quiet when the answer is complete")
        void saysNothingWhenUncapped(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 10);

            McpToolResult result = tools(dataSource).executeQuery("SELECT i FROM big");
            JsonNode answer = query(result);

            assertEquals(10, rowsReported(result.text()), result.text());
            assertEquals("COMPLETE", answer.get("truncation").asString());
            assertEquals("[\"1\"]", answer.get("rows").get(0).toString());
            assertEquals("[\"i\"]", answer.get("columns").toString());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer));
        }

        /**
         * The size cap stops before the next row, so crossing it on the last row loses nothing: the
         * answer is complete and must not tell the reader otherwise.
         */
        @Test
        @DisplayName("a last row that crosses the size cap is not reported as truncated")
        void doesNotCallACompleteAnswerTruncated(DataSource dataSource) {
            McpToolResult result = tools(dataSource).executeQuery("SELECT repeat('x', 60000) AS wide");

            assertEquals("COMPLETE", query(result).get("truncation").asString());
            assertEquals(1, rowsReported(result.text()));
        }

        @Test
        @DisplayName("says the output was truncated when rows remained unread")
        void announcesTruncationWhenRowsRemain(DataSource dataSource) {
            JsonNode answer = query(tools(dataSource)
                    .executeQuery("SELECT repeat('x', 30000) AS wide FROM generate_series(1, 3)"));

            assertEquals("OUTPUT_SIZE_LIMIT", answer.get("truncation").asString());
            assertEquals(2, answer.get("returned").asInt());
            assertTrue(StructuredAnswers.guidance(answer).contains("fewer columns"), answer.toString());
        }

        @Test
        @DisplayName("a caller's own smaller LIMIT still wins")
        void respectsACallersOwnLimit(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1500);

            String out = tools(dataSource).executeQuery("SELECT i FROM big LIMIT 5").text();

            assertEquals(5, rowsReported(out), out);
        }
    }

    /** A minimal events table and catalogue, shaped like the profile database's. */
    private static void seedEvents(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE event_types (name VARCHAR, label VARCHAR, description VARCHAR, "
                    + "categories VARCHAR)");
            stmt.execute("INSERT INTO event_types VALUES "
                    + "('jdk.ObjectAllocationSample', 'Allocation', 'A sampled allocation', 'Java'), "
                    + "('jdk.GCPhasePause', 'GC Pause', 'A pause', 'GC')");
            stmt.execute("CREATE TABLE events (event_type VARCHAR, start_timestamp BIGINT, duration BIGINT, "
                    + "samples BIGINT, weight BIGINT, weight_entity VARCHAR, stacktrace_hash BIGINT, "
                    + "thread_hash BIGINT, fields VARCHAR)");
            stmt.execute("INSERT INTO events VALUES "
                    + "('jdk.ObjectAllocationSample', 1000, 0, 1, 64, '" + LONG_CLASS_NAME + "', 1, 1, "
                    + "'{\"objectClass\":\"" + LONG_CLASS_NAME + "\",\"weight\":64}'), "
                    + "('jdk.ObjectAllocationSample', 2000, 0, 1, 32, 'byte[]', 1, 1, '{\"weight\":32}')");
        }
    }

    /** Sixty characters, well past the fifteen-character column every cell used to be cut to. */
    private static final String LONG_CLASS_NAME =
            "com.example.checkout.pricing.internal.DiscountRulesEvaluator";

    @Nested
    @DisplayName("Cells are rendered whole")
    class WholeCells {

        @Test
        @DisplayName("a 60-character class name survives jfr_executeQuery intact")
        void longValueSurvivesExecuteQuery(DataSource dataSource) throws SQLException {
            assertEquals(60, LONG_CLASS_NAME.length());
            seedEvents(dataSource);

            String out = tools(dataSource)
                    .executeQuery("SELECT weight_entity FROM events ORDER BY start_timestamp").text();

            assertTrue(out.contains("| " + LONG_CLASS_NAME + " |"), out);
            assertFalse(out.contains("..."), out);
            assertEquals(2, rowsReported(out), out);
        }

        @Test
        @DisplayName("the fields JSON of jfr_queryEvents survives intact, newest first")
        void fieldsSurviveQueryEvents(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            String out = tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 10, null).text();

            assertTrue(out.contains("{\"objectClass\":\"" + LONG_CLASS_NAME + "\",\"weight\":64}"), out);
            assertTrue(out.indexOf("| 2000 |") < out.indexOf("| 1000 |"), "newest first: " + out);
        }

        /**
         * One clamp convention across every tool: a limit of zero or below means the default, not one
         * row, because a model that sends 0 is not asking for a single event.
         */
        @Test
        @DisplayName("a non-positive limit of jfr_queryEvents takes the default")
        void nonPositiveLimitTakesTheDefault(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            String out = tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 0, null).text();

            assertTrue(out.contains("| 2000 |"), out);
            assertTrue(out.contains("| 1000 |"), out);
        }

        @Test
        @DisplayName("a pipe inside the fields JSON is escaped, not rewritten or dropped")
        void pipeInFieldsSurvives(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO events VALUES ('jdk.ObjectAllocationSample', 3000, 0, 1, 8, "
                        + "'int[]', 1, 1, '{\"pattern\":\"GET|POST\"}')");
            }

            String out = tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 1, null).text();

            assertTrue(out.contains("{\"pattern\":\"GET\\|POST\"}"), out);
            assertFalse(out.contains("GET/POST"), out);
        }

        @Test
        @DisplayName("a NULL cell says NULL rather than going blank")
        void nullIsSpelledOut(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            McpToolResult result = tools(dataSource).executeQuery("SELECT NULL AS nothing FROM big");

            assertTrue(result.text().contains("| NULL |"), result.text());
            assertTrue(query(result).get("rows").get(0).get(0).isNull(), "a SQL NULL is null in the record");
        }
    }

    @Nested
    @DisplayName("Event queries")
    class EventQueries {

        @Test
        @DisplayName("an event type the profile never recorded is the caller's mistake")
        void unknownEventTypeIsAnError(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> tools(dataSource).queryEvents("jdk.NoSuchEvent", 10, null));

            assertTrue(error.getMessage().contains("jfr_listEventTypes"), error.getMessage());
        }

        @Test
        @DisplayName("a known type with nothing matching is a status line, not an error")
        void knownTypeWithNoRowsIsAStatus(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            McpToolResult result = tools(dataSource).queryEvents("jdk.GCPhasePause", 10, null);
            JsonNode answer = events(result);

            assertTrue(result.text().startsWith("status: NO_EVENTS"), result.text());
            assertEquals("NO_EVENTS", answer.get("status").asString());
            assertEquals(0, answer.get("rows").size());
            assertEquals(List.of("jfr_listEventTypes"), StructuredAnswers.nextTools(answer));
        }

        @Test
        @DisplayName("a known type whose events a WHERE clause excludes is NO_MATCH, with the unfiltered call")
        void aFilterThatMatchesNothingIsNoMatch(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            JsonNode answer = events(tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 10, "weight > 1000"));

            assertEquals("NO_MATCH", answer.get("status").asString());
            assertEquals("weight > 1000", answer.get("whereClause").asString());
            JsonNode unfiltered = StructuredAnswers.call(answer, "jfr_queryEvents");
            assertFalse(unfiltered.has("whereClause"), unfiltered.toString());
        }

        @Test
        @DisplayName("events carry their rows typed, the latest kept, and say when more matched than the limit")
        void eventsSayWhenMoreMatched(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            JsonNode answer = events(tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 1, null));

            assertEquals("OK", answer.get("status").asString());
            assertEquals("ROW_LIMIT", answer.get("truncation").asString());
            assertEquals("2000", answer.get("rows").get(0).get(1).asString(), "the latest event is kept");
            assertTrue(answer.get("uiLink").asString().endsWith("/profiles/p-1/events?eventType=jdk.ObjectAllocationSample"),
                    answer.get("uiLink").asString());
            assertEquals("jdk.ObjectAllocationSample",
                    StructuredAnswers.call(answer, "jfr_describeEventType").get("eventType").asString());
        }

        @Test
        @DisplayName("every matching event under the limit is COMPLETE")
        void eventsUnderTheLimitAreComplete(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            JsonNode answer = events(tools(dataSource).queryEvents("jdk.ObjectAllocationSample", 10, null));

            assertEquals("COMPLETE", answer.get("truncation").asString());
            assertEquals(2, answer.get("returned").asInt());
        }

        @Test
        @DisplayName("the event-type list names the real tool, not a legacy alias")
        void listEventTypesNamesTheTool(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            JsonNode answer = StructuredAnswers.json(DuckDbMcpTools.class, "listEventTypes",
                    tools(dataSource).listEventTypes());

            assertEquals("jdk.ObjectAllocationSample", answer.get("eventTypes").get(0).get("name").asString());
            assertEquals(2, answer.get("eventTypes").get(0).get("count").asInt());
            assertEquals(List.of("jfr_describeEventType", "jfr_queryEvents"), StructuredAnswers.nextTools(answer));
            assertTrue(answer.get("uiLink").asString().endsWith("/profiles/p-1/events"), answer.toString());
        }

        @Test
        @DisplayName("the table list names the real tool, not a legacy alias")
        void listTablesNamesTheTool(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            JsonNode answer = StructuredAnswers.jsonWithoutPage(DuckDbMcpTools.class, "listTables",
                    tools(dataSource).listTables());

            assertEquals("events", StructuredAnswers.call(answer, "jfr_describeTable").get("tableName").asString());
            assertEquals("jeffrey://profile/p-1/schema", answer.get("schemaResource").asString());
            assertEquals("big", answer.get("tables").get(0).get("name").asString());
            assertFalse(answer.get("tables").get(0).get("view").asBoolean());
        }

        /** The answer names the very URI the resource links attach, so an id is encoded the same way. */
        @Test
        @DisplayName("the schema resource encodes the profile id as the resource links do")
        void schemaResourceEncodesTheProfileId(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);
            String profileId = "p 1/\u00e4";

            JsonNode answer = StructuredAnswers.jsonWithoutPage(DuckDbMcpTools.class, "listTables",
                    new DuckDbMcpTools(dataSource, profileId, EVERY_FAMILY).listTables());

            assertEquals("jeffrey://profile/p%201%2F%C3%A4/schema", answer.get("schemaResource").asString());
            assertEquals(McpResources.schemaUri(profileId), answer.get("schemaResource").asString());
        }

        @Test
        @DisplayName("a table is described with its columns and a sample query")
        void describesATable(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 1);

            JsonNode answer = StructuredAnswers.jsonWithoutPage(DuckDbMcpTools.class, "describeTable",
                    tools(dataSource).describeTable("big"));

            assertEquals("i", answer.get("columns").get(0).get("name").asString());
            assertEquals("SELECT * FROM big LIMIT 10",
                    StructuredAnswers.call(answer, "jfr_executeQuery").get("query").asString());
            assertTrue(answer.get("note").isNull());
        }

        @Test
        @DisplayName("the profile's identity says where it belongs and links its page")
        void identifiesTheProfile(DataSource dataSource) throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE profile_info (profile_id VARCHAR, project_id VARCHAR, workspace_id VARCHAR)");
                stmt.execute("INSERT INTO profile_info VALUES ('p-1', NULL, NULL)");
            }

            JsonNode answer = StructuredAnswers.json(DuckDbMcpTools.class, "getProfileInfo",
                    tools(dataSource).getProfileInfo());

            assertEquals("QUICK_ANALYSIS", answer.get("kind").asString());
            assertTrue(answer.get("projectId").isNull());
            assertTrue(answer.get("uiLink").asString().endsWith("/profiles/p-1"), answer.toString());
            assertEquals(List.of("profiles_summary"), StructuredAnswers.nextTools(answer));
        }
    }

    /*
     * Under a Turkish default locale "WITH".toLowerCase() is "wıth" with a dotless i, and the read
     * check refused an honest CTE.
     */
    @Nested
    @DisplayName("Locale")
    class LocaleIndependence {

        @Test
        @DisplayName("a WITH query is accepted under a Turkish default locale")
        void acceptsWithUnderTurkishLocale(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);
            Locale previous = Locale.getDefault();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try {
                String out = tools(dataSource)
                        .executeQuery("WITH x AS (SELECT i FROM big) SELECT COUNT(*) AS n FROM x").text();

                assertEquals(1, rowsReported(out), out);
            } finally {
                Locale.setDefault(previous);
            }
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

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools(dataSource).executeQuery("SELECT nope FROM big"));

            assertTrue(error.getMessage().contains("nope"),
                    "the caller has to be told which column: " + error.getMessage());
            assertFalse(error.getMessage().contains("pending query result"), error.getMessage());
        }

        @Test
        @DisplayName("an unknown table comes back named")
        void surfacesTheRealCatalogError(DataSource dataSource) {
            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools(dataSource).executeQuery("SELECT * FROM no_such_table"));

            assertTrue(error.getMessage().contains("no_such_table"), error.getMessage());
            assertFalse(error.getMessage().contains("pending query result"), error.getMessage());
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

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools(dataSource)
                            .executeQuery("SELECT i FROM big; DROP TABLE big"));

            assertTrue(error.getMessage().contains("Only one statement"), error.getMessage());
            assertTrue(tableExists(dataSource), "the DROP must not have run");
        }

        @Test
        @DisplayName("a trailing semicolon is a terminator, not a second statement")
        void allowsATrailingSemicolon(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            String out = tools(dataSource).executeQuery("SELECT i FROM big;  ").text();

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

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools(dataSource)
                            .queryEvents("jdk.ExecutionSample", 10, "1=1); DROP TABLE big; --"));

            assertTrue(error.getMessage().contains("Only one statement"), error.getMessage());
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

    /**
     * The prefix check is a message, not a boundary. DuckDB runs a CTE-prefixed DELETE through
     * executeQuery -- with RETURNING it even produces a result set -- and in autocommit mode the row
     * is gone by the time the driver answers. What stops it is the transaction the tool wraps every
     * caller-supplied statement in and rolls back whatever happened.
     */
    @Nested
    @DisplayName("Nothing a query does persists")
    class NothingPersists {

        private static final String CTE_DELETE_RETURNING =
                "WITH t AS (SELECT 1) DELETE FROM big WHERE i = 1 RETURNING i";
        private static final String CTE_DELETE = "WITH t AS (SELECT 1) DELETE FROM big WHERE i = 1";
        private static final String CTE_INSERT = "WITH t AS (SELECT 1) INSERT INTO big SELECT 99";

        @Test
        @DisplayName("a CTE-prefixed DELETE with RETURNING leaves the table as it was")
        void rollsBackADeleteWithReturning(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 10);

            runIgnoringRefusal(dataSource, CTE_DELETE_RETURNING);

            assertEquals(10, rowCount(dataSource));
        }

        @Test
        @DisplayName("a CTE-prefixed DELETE without RETURNING leaves the table as it was")
        void rollsBackADeleteWithoutReturning(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 10);

            runIgnoringRefusal(dataSource, CTE_DELETE);

            assertEquals(10, rowCount(dataSource));
        }

        @Test
        @DisplayName("a CTE-prefixed INSERT leaves the table as it was")
        void rollsBackAnInsert(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 10);

            runIgnoringRefusal(dataSource, CTE_INSERT);

            assertEquals(10, rowCount(dataSource));
        }

        @Test
        @DisplayName("the connection is handed back in autocommit, as the pool expects it")
        void restoresAutocommit(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            tools(dataSource).executeQuery("SELECT i FROM big");

            try (Connection conn = dataSource.getConnection()) {
                assertTrue(conn.getAutoCommit());
            }
        }

        @Test
        @DisplayName("an honest read still answers")
        void stillAnswersAPlainRead(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            String out = tools(dataSource).executeQuery("SELECT i FROM big ORDER BY i").text();

            assertEquals(3, rowsReported(out), out);
        }

        /**
         * Whether the engine answers the write or refuses it is the driver's business; what this
         * class asserts is the table afterwards.
         */
        private static void runIgnoringRefusal(DataSource dataSource, String sql) {
            try {
                tools(dataSource).executeQuery(sql);
            } catch (ToolExecutionException refused) {
                // A refusal is fine; a silently persisted write is the bug.
            }
        }

        private static int rowCount(DataSource dataSource) throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM big")) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Nested
    @DisplayName("Statement shape")
    class StatementShape {

        @Test
        @DisplayName("refuses anything that is not a SELECT or a WITH")
        void refusesNonSelect(DataSource dataSource) {
            DuckDbMcpTools tools = tools(dataSource);

            assertThrows(ToolExecutionException.class, () -> tools.executeQuery("DELETE FROM big"));
            assertThrows(ToolExecutionException.class, () -> tools.executeQuery("ATTACH 'other.db' AS other"));
            assertThrows(ToolExecutionException.class, () -> tools.executeQuery("COPY big TO '/tmp/out.csv'"));
        }

        @Test
        @DisplayName("accepts a WITH, which is a read")
        void acceptsWith(DataSource dataSource) throws SQLException {
            seedRows(dataSource, 3);

            String out = tools(dataSource)
                    .executeQuery("WITH x AS (SELECT i FROM big) SELECT COUNT(*) FROM x").text();

            assertFalse(out.startsWith("Error:"), out);
        }

    }
}
