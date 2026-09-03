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

package cafe.jeffrey.profile.ai.duckdb.jfr.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DuckDB tools for AI-powered JFR profile analysis.
 * Provides methods that can be called by AI models to query and analyze JFR events stored in DuckDB.
 */
public class DuckDbMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbMcpTools.class);

    private static final int MAX_ROWS = 1000;
    private static final int MAX_QUERY_RESULT_LENGTH = 50000;

    /**
     * How long a caller's query may run. Matches the heap side's SqlExecutor, which is the existing
     * precedent. Without it a cartesian join holds one of the profile pool's connections until the
     * process dies, and that pool is the one the UI reads the same profile through.
     */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final String MULTIPLE_STATEMENTS_MESSAGE =
            "Only one statement per call. Send the SELECT on its own, without a second statement "
                    + "after a semicolon.";

    private final DataSource dataSource;
    private final boolean canModify;

    public DuckDbMcpTools(DataSource dataSource) {
        this(dataSource, false);
    }

    public DuckDbMcpTools(DataSource dataSource, boolean canModify) {
        this.dataSource = dataSource;
        this.canModify = canModify;
    }

    @Tool(description = "List all tables in the JFR profile database. Returns table names that can be queried.")
    public String listTables() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                StringBuilder result = new StringBuilder("Tables in the JFR profile database:\n\n");
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Skip DuckDB internal tables
                    if (!tableName.startsWith("flyway_")) {
                        result.append("- ").append(tableName).append("\n");
                    }
                }
                result.append("\nUse describe_table to get the schema of a specific table.");
                return result.toString();
            }
        } catch (SQLException e) {
            LOG.error("Failed to list tables: message={}", e.getMessage(), e);
            return "Error: Failed to list tables: " + e.getMessage();
        }
    }

    @Tool(description = "Get the schema of a specific table including column names, types, and nullability. " +
            "Use this before querying to understand the table structure.")
    public String describeTable(
            @ToolParam(description = "Name of the table to describe (e.g., 'events', 'threads', 'frames')")
            String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Error: Table name is required";
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
                StringBuilder result = new StringBuilder("Schema for table '").append(tableName).append("':\n\n");
                result.append(String.format("%-25s %-20s %-10s%n", "COLUMN", "TYPE", "NULLABLE"));
                result.append("-".repeat(55)).append("\n");

                boolean hasColumns = false;
                while (rs.next()) {
                    hasColumns = true;
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    String nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO";
                    result.append(String.format("%-25s %-20s %-10s%n", columnName, typeName, nullable));
                }

                if (!hasColumns) {
                    return "Error: Table '" + tableName + "' not found";
                }

                // Add helpful notes for specific tables
                if ("events".equalsIgnoreCase(tableName)) {
                    result.append("\nNote: The 'fields' column contains event-specific data as JSON. ")
                            .append("Use DuckDB JSON functions to extract values, e.g., ")
                            .append("fields->>'key' or json_extract(fields, '$.key')");
                }

                return result.toString();
            }
        } catch (SQLException e) {
            LOG.error("Failed to describe table: table={} message={}", tableName, e.getMessage(), e);
            return "Error: Failed to describe table: " + e.getMessage();
        }
    }

    @Tool(description = "Execute a read-only SQL query on the JFR profile database. " +
            "Only SELECT statements are allowed. Results are limited to " + MAX_ROWS + " rows. " +
            "The 'events' table contains JFR events with a JSON 'fields' column for event-specific data. " +
            "IMPORTANT: When using aggregate functions (COUNT, SUM, AVG, MIN, MAX), all non-aggregated columns " +
            "in the SELECT must appear in the GROUP BY clause.")
    public String executeQuery(
            @ToolParam(description = "SQL SELECT query to execute. Must be a read-only query.")
            String query) {
        if (query == null || query.isBlank()) {
            return "Error: Query is required";
        }

        // Defence in depth, and deliberately not the boundary. What confines this query is the
        // connection: the profile DataSource disables DuckDB's external file access and extension
        // autoloading, so no spelling of a SELECT reaches read_text, glob or ATTACH. A prefix test
        // could never do that on its own -- it is a string check, not a parser, and the reach is a
        // function call rather than a leading keyword. It stays only to turn an obvious write into
        // a clear message instead of an engine error.
        String normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery.startsWith("select") && !normalizedQuery.startsWith("with")) {
            return "Error: Only SELECT and WITH queries are allowed";
        }

        // prepareStatement, not createStatement, for three reasons. DuckDB refuses a prepared
        // statement carrying more than one statement, so ';' cannot smuggle a second one in. The row
        // cap and the timeout are enforced by the driver rather than by appending text, which the
        // old "does the query contain the word limit" test got wrong in both directions -- a query
        // mentioning it in a comment or an alias went uncapped, and a trailing line comment
        // swallowed the appended clause. And Statement.executeQuery reports every binder failure as
        // "unsuccessful or closed pending query result", where a prepared statement surfaces the
        // engine's real message: the missing column, or the permission error from the sandbox above.
        // That message is the only thing the caller can act on.
        if (carriesMultipleStatements(query)) {
            return "Error: " + MULTIPLE_STATEMENTS_MESSAGE;
        }

        // prepareStatement, not createStatement, for the error messages: Statement.executeQuery
        // reports a missing column, a missing table and a refusal from the sandbox identically, as
        // "unsuccessful or closed pending query result", where a prepared statement carries the
        // engine's real message. That message is the only thing a caller can correct itself from.
        //
        // The row cap is applied while reading, not with setMaxRows: DuckDB's driver accepts that
        // call and ignores it (getMaxRows stays 0). Reading is cheap because results stream, so
        // stopping at the cap does not make the engine materialise the rest.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            try (ResultSet rs = stmt.executeQuery()) {
                return formatResultSet(rs);
            }
        } catch (SQLException e) {
            LOG.error("Failed to execute query: query={} message={}", query, e.getMessage(), e);
            return "Error: Query execution failed: " + e.getMessage();
        }
    }

    @Tool(description = "List all JFR event types present in this profile with their counts and descriptions. " +
            "Use this to understand what events are available for analysis.")
    public String listEventTypes() {
        String query = """
                SELECT
                    et.name as event_type,
                    et.label,
                    et.description,
                    et.categories,
                    COALESCE(e.event_count, 0) as event_count,
                    COALESCE(e.total_samples, 0) as total_samples
                FROM event_types et
                LEFT JOIN (
                    SELECT
                        event_type,
                        COUNT(*) as event_count,
                        SUM(samples) as total_samples
                    FROM events
                    GROUP BY event_type
                ) e ON et.name = e.event_type
                ORDER BY e.event_count DESC NULLS LAST, et.name
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            StringBuilder result = new StringBuilder("JFR Event Types in this profile:\n\n");
            result.append(String.format("%-45s %-10s %-12s %s%n", "EVENT TYPE", "COUNT", "SAMPLES", "DESCRIPTION"));
            result.append("-".repeat(100)).append("\n");

            while (rs.next()) {
                String eventType = rs.getString("event_type");
                String label = rs.getString("label");
                String description = rs.getString("description");
                long count = rs.getLong("event_count");
                long samples = rs.getLong("total_samples");

                String desc = description != null ? description : (label != null ? label : "");
                if (desc.length() > 40) {
                    desc = desc.substring(0, 37) + "...";
                }

                result.append(String.format("%-45s %-10d %-12d %s%n", eventType, count, samples, desc));
            }

            result.append("\nUse query_events with an event_type to get detailed event data.");
            return result.toString();
        } catch (SQLException e) {
            LOG.error("Failed to list event types: message={}", e.getMessage(), e);
            return "Error: Failed to list event types: " + e.getMessage();
        }
    }

    @Tool(description = "Query JFR events by type with optional filtering. Returns event data including timestamps, " +
            "durations, samples, and JSON fields. Use list_event_types first to see available event types.")
    public String queryEvents(
            @ToolParam(description = "JFR event type name (e.g., 'jdk.ExecutionSample', 'jdk.GCPhasePause')")
            String eventType,
            @ToolParam(description = "Maximum number of events to return (default: 100, max: " + MAX_ROWS + ")")
            Integer limit,
            @ToolParam(description = "Optional SQL WHERE clause for filtering (without 'WHERE' keyword). " +
                    "Use column names exactly as they exist in the events table (e.g., 'duration', NOT 'duration_ns'). " +
                    "The duration column stores nanoseconds as BIGINT.")
            String whereClause) {

        if (eventType == null || eventType.isBlank()) {
            return "Error: Event type is required";
        }

        int effectiveLimit = limit != null ? limit : 100;
        int safeLimit = Math.min(Math.max(1, effectiveLimit), MAX_ROWS);

        StringBuilder queryBuilder = new StringBuilder("""
                SELECT
                    event_type,
                    start_timestamp,
                    duration,
                    samples,
                    weight,
                    weight_entity,
                    stacktrace_hash,
                    thread_hash,
                    fields
                FROM events
                WHERE event_type = ?
                """);

        if (whereClause != null && !whereClause.isBlank() && carriesMultipleStatements(whereClause)) {
            return "Error: " + MULTIPLE_STATEMENTS_MESSAGE;
        }

        if (whereClause != null && !whereClause.isBlank()) {
            // The fragment is caller-supplied SQL spliced into the statement, and it is confined by
            // the same thing executeQuery is: the connection has no filesystem and no extension
            // loading, so the worst a fragment can do is read this profile's own tables -- which is
            // what the tool is for. The keyword denylist that used to stand here was worse than
            // nothing: it missed ATTACH, COPY and every file function, so a scalar subquery walked
            // straight through the AND (...) it lands in, while it rejected honest filters over any
            // value containing "created" or "updated".
            queryBuilder.append(" AND (").append(whereClause).append(")");
        }

        queryBuilder.append(" ORDER BY start_timestamp DESC LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {

            stmt.setString(1, eventType);
            stmt.setInt(2, safeLimit);
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            try (ResultSet rs = stmt.executeQuery()) {
                return formatResultSet(rs);
            }
        } catch (SQLException e) {
            LOG.error("Failed to query events: eventType={} message={}", eventType, e.getMessage(), e);
            return "Error: Failed to query events: " + e.getMessage();
        }
    }

    @Tool(description = "Get information about the current JFR profile including profile ID, project ID, and workspace ID.")
    public String getProfileInfo() {
        String query = "SELECT profile_id, project_id, workspace_id FROM profile_info LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                String profileId = rs.getString("profile_id");
                String projectId = rs.getString("project_id");
                String workspaceId = rs.getString("workspace_id");

                return String.format("""
                        Profile Information:

                        Profile ID:   %s
                        Project ID:   %s
                        Workspace ID: %s

                        This is a %s profile.
                        """,
                        profileId,
                        projectId != null ? projectId : "N/A",
                        workspaceId != null ? workspaceId : "N/A",
                        projectId != null ? "regular" : "Recordings"
                );
            } else {
                return "Error: No profile information found";
            }
        } catch (SQLException e) {
            LOG.error("Failed to get profile info: message={}", e.getMessage(), e);
            return "Error: Failed to get profile info: " + e.getMessage();
        }
    }

    @Tool(description = "Execute a data modification query (UPDATE or DELETE) on the JFR profile database. " +
            "Use this to remove events, obfuscate frame names, anonymize thread names, or clean up data. " +
            "This tool is only available when modification mode is explicitly enabled by the user. " +
            "A WHERE clause is required to prevent accidental full-table modifications.")
    public String executeModification(
            @ToolParam(description = "SQL UPDATE or DELETE query. Must include a WHERE clause for safety.")
            String query) {

        if (!canModify) {
            return "Error: Data modification is not enabled. The user must enable 'Allow Modifications' in the UI to use this tool.";
        }

        if (query == null || query.isBlank()) {
            return "Error: Query is required";
        }

        String normalizedQuery = query.trim().toLowerCase();

        // Only allow UPDATE and DELETE
        if (!normalizedQuery.startsWith("update") && !normalizedQuery.startsWith("delete")) {
            return "Error: Only UPDATE and DELETE queries are allowed. Use executeQuery for SELECT statements.";
        }

        // Require WHERE clause for safety
        if (!normalizedQuery.contains("where")) {
            return "Error: A WHERE clause is required to prevent accidental full-table modifications. " +
                    "If you really want to affect all rows, use 'WHERE 1=1' explicitly.";
        }

        // Prevent modifications to system tables
        if (normalizedQuery.contains("flyway_") || normalizedQuery.contains("profile_info")) {
            return "Error: Modifications to system tables (flyway_*, profile_info) are not allowed.";
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            int affectedRows = stmt.executeUpdate(query);

            LOG.info("Executed modification query: query={} affectedRows={}", query, affectedRows);

            return String.format("Successfully executed modification. %d row(s) affected.", affectedRows);
        } catch (SQLException e) {
            LOG.error("Failed to execute modification: query={} message={}", query, e.getMessage(), e);
            return "Error: Modification failed: " + e.getMessage();
        }
    }

    private String formatResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        StringBuilder result = new StringBuilder();
        List<String> headers = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();

        // Collect headers
        for (int i = 1; i <= columnCount; i++) {
            String header = metaData.getColumnLabel(i);
            headers.add(header);
            widths.add(Math.max(header.length(), 15));
        }

        // Build header row
        for (int i = 0; i < headers.size(); i++) {
            result.append(String.format("%-" + widths.get(i) + "s ", headers.get(i)));
        }
        result.append("\n");
        result.append("-".repeat(widths.stream().mapToInt(Integer::intValue).sum() + widths.size())).append("\n");

        // Build data rows. Two caps: the row count, and the size of the rendered output.
        int rowCount = 0;
        while (rowCount < MAX_ROWS && result.length() < MAX_QUERY_RESULT_LENGTH && rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                String strValue = value != null ? value.toString() : "NULL";
                if (strValue.length() > widths.get(i - 1)) {
                    strValue = strValue.substring(0, widths.get(i - 1) - 3) + "...";
                }
                result.append(String.format("%-" + widths.get(i - 1) + "s ", strValue));
            }
            result.append("\n");
            rowCount++;
        }

        if (result.length() >= MAX_QUERY_RESULT_LENGTH) {
            result.append("\n... (output truncated, ").append(rowCount).append(" rows shown)");
        } else if (rowCount == MAX_ROWS && rs.next()) {
            // Asked once more, so this says what is true rather than what is likely. A capped answer
            // is otherwise indistinguishable from a complete one, and a reader that cannot see the
            // cap reports the first page as the whole story.
            result.append("\n").append(rowCount)
                    .append(" row(s) returned - the ").append(MAX_ROWS)
                    .append(" row cap was reached and there are more. ")
                    .append("Aggregate in SQL rather than pulling rows back to count them.");
        } else {
            result.append("\n").append(rowCount).append(" row(s) returned");
        }

        return result.toString();
    }

    /**
     * Whether the text carries more than one statement.
     * <p>
     * DuckDB's driver runs every statement in the string and only afterwards complains that
     * {@code executeQuery} produced no result set — by which time the second one has already run.
     * {@code SELECT 1; DROP TABLE events} therefore satisfies a leading-keyword check and still drops
     * the table, which is how a tool documented as read-only turns out to write. Nothing else here
     * stops it: the engine sandbox blocks the filesystem, not DDL against this database.
     * <p>
     * A semicolon inside a string literal, a quoted identifier or a comment is not a separator, so
     * those are masked out before looking. A single trailing semicolon is a statement terminator, not
     * a second statement.
     */
    static boolean carriesMultipleStatements(String sql) {
        String masked = maskLiteralsAndComments(sql);
        int separator = masked.indexOf(';');
        return separator >= 0 && !masked.substring(separator + 1).isBlank();
    }

    /**
     * The same text with every string literal, quoted identifier and comment blanked out, keeping the
     * original length so positions still line up. Anything unterminated blanks to the end, which
     * hides a semicolon rather than inventing one — the safe direction here is to under-report a
     * separator inside a malformed query, since DuckDB rejects the query anyway.
     */
    private static String maskLiteralsAndComments(String sql) {
        char[] out = sql.toCharArray();
        int i = 0;
        while (i < out.length) {
            char current = out[i];
            if (current == '\'' || current == '"') {
                i = maskUntil(out, i, current);
            } else if (current == '-' && i + 1 < out.length && out[i + 1] == '-') {
                while (i < out.length && out[i] != '\n') {
                    out[i++] = ' ';
                }
            } else if (current == '/' && i + 1 < out.length && out[i + 1] == '*') {
                i = maskBlockComment(out, i);
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** Blanks a quoted run, including both quotes, and returns the index just past it. */
    private static int maskUntil(char[] out, int start, char quote) {
        out[start] = ' ';
        int i = start + 1;
        while (i < out.length) {
            boolean closing = out[i] == quote;
            out[i++] = ' ';
            if (closing) {
                return i;
            }
        }
        return i;
    }

    private static int maskBlockComment(char[] out, int start) {
        int i = start;
        out[i++] = ' ';
        while (i < out.length) {
            boolean closing = out[i] == '*' && i + 1 < out.length && out[i + 1] == '/';
            out[i++] = ' ';
            if (closing) {
                out[i++] = ' ';
                return i;
            }
        }
        return i;
    }
}
