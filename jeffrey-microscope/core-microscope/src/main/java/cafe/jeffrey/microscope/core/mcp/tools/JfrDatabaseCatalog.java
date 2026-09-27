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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the JFR profile database holds, as a query sees it: its tables and views, the columns of each,
 * and the event types with how many events of each it stores.
 * <p>
 * The one place these reads are written. The {@code jfr_} catalogue tools render them as text, and
 * the {@code jeffrey://profile/{profileId}/schema} resource returns them as one document, so the
 * tools and the resource cannot disagree about what the database contains.
 */
final class JfrDatabaseCatalog {

    /** The relation every reader queries: a view over {@code events_raw} that splices pooled fields back. */
    static final String EVENTS_VIEW = "events";

    /** What a reader needs to know about {@code events} before writing a query against it. */
    static final String EVENTS_FIELDS_NOTE = "The 'fields' column contains event-specific data as JSON. "
            + "Use DuckDB JSON functions to extract values, e.g., fields->>'key' or "
            + "json_extract(fields, '$.key'). jfr_describeEventType names the keys of one event type.";

    /**
     * Both kinds, because DuckDB's driver reports a view as {@code VIEW}: asking for tables alone left
     * out {@code events}, the view every query is written against.
     */
    private static final String TABLE_TYPE = "TABLE";
    private static final String VIEW_TYPE = "VIEW";
    private static final String[] RELATION_TYPES = {TABLE_TYPE, VIEW_TYPE};
    private static final String ANY_NAME = "%";
    private static final String COLUMN_TABLE_NAME = "TABLE_NAME";
    private static final String COLUMN_TABLE_TYPE = "TABLE_TYPE";

    /** The migration tool's bookkeeping, not the profile's data. */
    private static final String INTERNAL_TABLE_PREFIX = "flyway_";

    private static final String NULLABLE = "YES";

    /**
     * How long one catalogue read may run. The same bound the caller-SQL tools use, since these reads
     * go through the same pool the UI reads the profile through.
     */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /**
     * Bound as a value rather than passed to {@code DatabaseMetaData.getColumns}, whose table
     * argument is a LIKE pattern: a name holding {@code %} or {@code _} described every table that
     * matched it instead of reporting that no such table exists.
     */
    private static final String DESCRIBE_COLUMNS =
            "SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                    + "WHERE table_name = ? AND table_schema = current_schema() ORDER BY ordinal_position";

    private static final String LIST_EVENT_TYPES = """
            SELECT
                et.name as event_type,
                et.label,
                et.description,
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

    /**
     * @param view whether the relation is a view rather than a stored table
     */
    record Relation(String name, boolean view) {
    }

    private JfrDatabaseCatalog() {
    }

    /**
     * Every table and view of the current schema a query can read, tables first, each kind by name.
     */
    static List<Relation> relations(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        List<Relation> relations = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(null, conn.getSchema(), ANY_NAME, RELATION_TYPES)) {
            while (rs.next()) {
                String name = rs.getString(COLUMN_TABLE_NAME);
                if (!name.startsWith(INTERNAL_TABLE_PREFIX)) {
                    relations.add(new Relation(name, VIEW_TYPE.equals(rs.getString(COLUMN_TABLE_TYPE))));
                }
            }
        }
        return List.copyOf(relations);
    }

    /**
     * The columns of one table or view in declaration order; empty when no relation goes by that name.
     */
    static List<ProfileSchema.Column> columns(Connection conn, String relation) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(DESCRIBE_COLUMNS)) {
            stmt.setString(1, relation);
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            List<ProfileSchema.Column> columns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(new ProfileSchema.Column(
                            rs.getString(1), rs.getString(2), NULLABLE.equals(rs.getString(3))));
                }
            }
            return List.copyOf(columns);
        }
    }

    /**
     * Every event type the recording declared, with how many events of it the database stores and
     * their samples; the most frequent first, and a declared type with no events last at zero.
     */
    static List<ProfileSchema.EventType> eventTypes(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(LIST_EVENT_TYPES)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            List<ProfileSchema.EventType> eventTypes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    eventTypes.add(new ProfileSchema.EventType(
                            rs.getString("event_type"),
                            rs.getString("label"),
                            rs.getString("description"),
                            rs.getLong("event_count"),
                            rs.getLong("total_samples")));
                }
            }
            return List.copyOf(eventTypes);
        }
    }

    /**
     * What a reader should know about one relation before querying it, when there is anything.
     */
    static Optional<String> note(String relation) {
        return EVENTS_VIEW.equalsIgnoreCase(relation) ? Optional.of(EVENTS_FIELDS_NOTE) : Optional.empty();
    }
}
