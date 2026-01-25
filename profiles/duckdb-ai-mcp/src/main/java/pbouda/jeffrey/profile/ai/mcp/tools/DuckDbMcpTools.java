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

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP tools for DuckDB database operations on JFR profile data.
 * Provides AI models with the ability to query and analyze JFR events stored in DuckDB.
 */
public class DuckDbMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbMcpTools.class);

    private static final int MAX_ROWS = 1000;
    private static final int MAX_QUERY_RESULT_LENGTH = 50000;

    private final DataSource dataSource;

    public DuckDbMcpTools(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns all available MCP tool specifications for this profile database.
     */
    public List<SyncToolSpecification> getToolSpecifications() {
        return List.of(
                createListTablesSpec(),
                createDescribeTableSpec(),
                createExecuteQuerySpec(),
                createListEventTypesSpec(),
                createQueryEventsSpec(),
                createGetProfileInfoSpec()
        );
    }

    private SyncToolSpecification createListTablesSpec() {
        Tool tool = new Tool(
                "list_tables",
                "List all tables in the JFR profile database. Returns table names that can be queried.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                )
        );
        return new SyncToolSpecification(tool, args -> listTables());
    }

    private SyncToolSpecification createDescribeTableSpec() {
        Tool tool = new Tool(
                "describe_table",
                "Get the schema of a specific table including column names, types, and nullability. " +
                        "Use this before querying to understand the table structure.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "table_name", Map.of(
                                        "type", "string",
                                        "description", "Name of the table to describe (e.g., 'events', 'threads', 'frames')"
                                )
                        ),
                        "required", List.of("table_name")
                )
        );
        return new SyncToolSpecification(tool, args -> describeTable((String) args.get("table_name")));
    }

    private SyncToolSpecification createExecuteQuerySpec() {
        Tool tool = new Tool(
                "execute_query",
                "Execute a read-only SQL query on the JFR profile database. " +
                        "Only SELECT statements are allowed. Results are limited to " + MAX_ROWS + " rows. " +
                        "The 'events' table contains JFR events with a JSON 'fields' column for event-specific data.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "SQL SELECT query to execute. Must be a read-only query."
                                )
                        ),
                        "required", List.of("query")
                )
        );
        return new SyncToolSpecification(tool, args -> executeQuery((String) args.get("query")));
    }

    private SyncToolSpecification createListEventTypesSpec() {
        Tool tool = new Tool(
                "list_event_types",
                "List all JFR event types present in this profile with their counts and descriptions. " +
                        "Use this to understand what events are available for analysis.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                )
        );
        return new SyncToolSpecification(tool, args -> listEventTypes());
    }

    private SyncToolSpecification createQueryEventsSpec() {
        Tool tool = new Tool(
                "query_events",
                "Query JFR events by type with optional filtering. Returns event data including timestamps, " +
                        "durations, samples, and JSON fields. Use list_event_types first to see available event types.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "event_type", Map.of(
                                        "type", "string",
                                        "description", "JFR event type name (e.g., 'jdk.ExecutionSample', 'jdk.GCPhasePause')"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "Maximum number of events to return (default: 100, max: " + MAX_ROWS + ")"
                                ),
                                "where_clause", Map.of(
                                        "type", "string",
                                        "description", "Optional SQL WHERE clause for filtering (without 'WHERE' keyword)"
                                )
                        ),
                        "required", List.of("event_type")
                )
        );
        return new SyncToolSpecification(tool, args -> queryEvents(
                (String) args.get("event_type"),
                args.get("limit") != null ? ((Number) args.get("limit")).intValue() : 100,
                (String) args.get("where_clause")
        ));
    }

    private SyncToolSpecification createGetProfileInfoSpec() {
        Tool tool = new Tool(
                "get_profile_info",
                "Get information about the current JFR profile including profile ID, project ID, and workspace ID.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                )
        );
        return new SyncToolSpecification(tool, args -> getProfileInfo());
    }

    private CallToolResult listTables() {
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
                return createTextResult(result.toString());
            }
        } catch (SQLException e) {
            LOG.error("Failed to list tables: message={}", e.getMessage(), e);
            return createErrorResult("Failed to list tables: " + e.getMessage());
        }
    }

    private CallToolResult describeTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return createErrorResult("Table name is required");
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
                    return createErrorResult("Table '" + tableName + "' not found");
                }

                // Add helpful notes for specific tables
                if ("events".equalsIgnoreCase(tableName)) {
                    result.append("\nNote: The 'fields' column contains event-specific data as JSON. ")
                            .append("Use DuckDB JSON functions to extract values, e.g., ")
                            .append("fields->>'key' or json_extract(fields, '$.key')");
                }

                return createTextResult(result.toString());
            }
        } catch (SQLException e) {
            LOG.error("Failed to describe table: table={} message={}", tableName, e.getMessage(), e);
            return createErrorResult("Failed to describe table: " + e.getMessage());
        }
    }

    private CallToolResult executeQuery(String query) {
        if (query == null || query.isBlank()) {
            return createErrorResult("Query is required");
        }

        String normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery.startsWith("select") && !normalizedQuery.startsWith("with")) {
            return createErrorResult("Only SELECT queries are allowed for security reasons");
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
            return createErrorResult("Query execution failed: " + e.getMessage());
        }
    }

    private CallToolResult listEventTypes() {
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
            return createTextResult(result.toString());
        } catch (SQLException e) {
            LOG.error("Failed to list event types: message={}", e.getMessage(), e);
            return createErrorResult("Failed to list event types: " + e.getMessage());
        }
    }

    private CallToolResult queryEvents(String eventType, int limit, String whereClause) {
        if (eventType == null || eventType.isBlank()) {
            return createErrorResult("Event type is required");
        }

        int safeLimit = Math.min(Math.max(1, limit), MAX_ROWS);

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
                return createErrorResult("Invalid WHERE clause: contains forbidden keywords");
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
            return createErrorResult("Failed to query events: " + e.getMessage());
        }
    }

    private CallToolResult getProfileInfo() {
        String query = "SELECT profile_id, project_id, workspace_id FROM profile_info LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                String profileId = rs.getString("profile_id");
                String projectId = rs.getString("project_id");
                String workspaceId = rs.getString("workspace_id");

                String result = String.format("""
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
                return createTextResult(result);
            } else {
                return createErrorResult("No profile information found");
            }
        } catch (SQLException e) {
            LOG.error("Failed to get profile info: message={}", e.getMessage(), e);
            return createErrorResult("Failed to get profile info: " + e.getMessage());
        }
    }

    private CallToolResult formatResultSet(ResultSet rs) throws SQLException {
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

        return createTextResult(result.toString());
    }

    private CallToolResult createTextResult(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false);
    }

    private CallToolResult createErrorResult(String error) {
        return new CallToolResult(List.of(new TextContent("Error: " + error)), true);
    }
}
