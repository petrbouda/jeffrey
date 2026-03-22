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

package pbouda.jeffrey.profile.ai.mcp.tools;

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

        String normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery.startsWith("select") && !normalizedQuery.startsWith("with")) {
            return "Error: Only SELECT queries are allowed for security reasons";
        }

        // Add LIMIT if not present
        String safeQuery = query;
        if (!normalizedQuery.contains("limit")) {
            safeQuery = query + " LIMIT " + MAX_ROWS;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(safeQuery)) {

            return formatResultSet(rs);
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

        if (whereClause != null && !whereClause.isBlank()) {
            // Basic SQL injection prevention
            String sanitized = whereClause.toLowerCase();
            if (sanitized.contains("drop") || sanitized.contains("delete") ||
                    sanitized.contains("insert") || sanitized.contains("update") ||
                    sanitized.contains("alter") || sanitized.contains("create")) {
                return "Error: Invalid WHERE clause: contains forbidden keywords";
            }
            queryBuilder.append(" AND (").append(whereClause).append(")");
        }

        queryBuilder.append(" ORDER BY start_timestamp DESC LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {

            stmt.setString(1, eventType);
            stmt.setInt(2, safeLimit);

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
                        projectId != null ? "regular" : "Quick Analysis"
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

        // Build data rows
        int rowCount = 0;
        while (rs.next() && result.length() < MAX_QUERY_RESULT_LENGTH) {
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
        } else {
            result.append("\n").append(rowCount).append(" row(s) returned");
        }

        return result.toString();
    }
}
