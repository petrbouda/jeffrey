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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.McpResources;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.EvidenceOutput.TruncationReason;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpNullableElement;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@code jfr_} SQL family of the MCP server: read-only DuckDB access to the events a profile
 * stores, for the questions no dashboard tool answers. Every tool here reads; the family has no
 * write tool, so an external client gets to read a profile's data, never to rewrite it.
 * <p>
 * The catalogue tools answer records whose text is their JSON. The two query tools keep the Markdown
 * table a model reads beside the record, which carries the same rows typed: the columns, one array of
 * cells per row as text with a SQL NULL as null, and why the rows stop where they do. The database's
 * tables have no Microscope page, so the catalogue tools link the {@code schema} resource instead; the
 * event types and the events of one type open the event viewer.
 */
public class DuckDbMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbMcpTools.class);

    private static final int MAX_ROWS = 1000;
    private static final int DEFAULT_EVENT_ROWS = 100;
    private static final int MAX_QUERY_RESULT_LENGTH = 50000;

    /**
     * How long a caller's query may run. Matches the heap side's SqlExecutor, which is the existing
     * precedent. Without it a cartesian join holds one of the profile pool's connections until the
     * process dies, and that pool is the one the UI reads the same profile through.
     */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final String QUERY_EVENTS_BASE = """
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
            """;
    private static final String WHERE_FRAGMENT_OPEN = " AND (";
    private static final String WHERE_FRAGMENT_CLOSE = ")";
    private static final String QUERY_EVENTS_ORDER_AND_LIMIT = " ORDER BY start_timestamp DESC LIMIT ?";

    private static final String EVENT_TYPE_KNOWN =
            "SELECT 1 FROM event_types WHERE name = ? LIMIT 1";

    private static final String PROFILE_INFO =
            "SELECT profile_id, project_id, workspace_id FROM profile_info LIMIT 1";

    private static final String READ_PREFIX_SELECT = "select";
    private static final String READ_PREFIX_WITH = "with";

    /** How a NULL cell reads in the table: an empty cell would be indistinguishable from an empty string. */
    private static final String NULL_CELL = "NULL";

    private static final String STATUS_PREFIX = "status: ";
    private static final String LINE_BREAK = "\n";

    /** The cost of a cell beyond its own characters in the size cap: the separators around it. */
    private static final int CELL_OVERHEAD_CHARS = 3;

    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String SAMPLE_QUERY = "SELECT * FROM %s LIMIT 10";

    private static final String DESCRIBE_TABLE = "jfr_describeTable";
    private static final String EXECUTE_QUERY = "jfr_executeQuery";
    private static final String LIST_EVENT_TYPES = "jfr_listEventTypes";
    private static final String QUERY_EVENTS = "jfr_queryEvents";
    private static final String DESCRIBE_EVENT_TYPE = "jfr_describeEventType";
    private static final String PROFILES_SUMMARY = "profiles_summary";
    private static final String PROFILE_ID = "profileId";
    private static final String TABLE_NAME = "tableName";
    private static final String QUERY = "query";
    private static final String EVENT_TYPE = "eventType";
    private static final String LIMIT = "limit";
    private static final int SAMPLE_EVENTS = 10;

    private static final String DESCRIBE_EVENTS_WHY = "names the columns of the events view every query reads";
    private static final String SAMPLE_WHY = "reads the first ten rows, to see what the columns hold";
    private static final String EVENT_TYPES_WHY = "names the event types this profile recorded, with their counts";
    private static final String FIELDS_WHY = "names the keys of this event type's JSON fields column";
    private static final String SAMPLE_EVENTS_WHY = "reads the ten latest events of the most frequent type";
    private static final String UNFILTERED_WHY = "reads the latest events of the type without the filter";
    private static final String SUMMARY_WHY = "orients in the profile: what was recorded and what stands out";

    private static final String ROW_LIMIT_GUIDANCE =
            "The row cap was reached and more rows remain. Aggregate in SQL rather than pulling rows back to count them.";
    private static final String SIZE_LIMIT_GUIDANCE =
            "The rows stop at the response size limit and more remain. Select fewer columns or aggregate in SQL.";
    private static final String EVENT_LIMIT_GUIDANCE =
            "More events matched than the limit; these are the latest. Raise limit, narrow whereClause, or aggregate "
                    + "with jfr_executeQuery.";

    private static final String UNKNOWN_EVENT_TYPE =
            "This profile recorded no event type called '%s'. jfr_listEventTypes names the ones it "
                    + "has, with their counts.";
    private static final String NO_EVENTS_OF_TYPE = "The profile knows %s but holds no event of it.";
    private static final String NO_EVENTS_MATCHED =
            "No %s event matched the WHERE clause; the event type itself is one this profile knows.";

    private static final String MULTIPLE_STATEMENTS_MESSAGE =
            "Only one statement per call. Send the SELECT on its own, without a second statement "
                    + "after a semicolon.";

    private final DataSource dataSource;
    private final String profileId;
    private final AdvertisedFamilies advertised;

    public DuckDbMcpTools(DataSource dataSource, String profileId, AdvertisedFamilies advertised) {
        this.dataSource = dataSource;
        this.profileId = profileId;
        this.advertised = advertised;
    }

    @Tool(description = "Names the tables and views of the JFR profile database that a query can read, "
            + "the events view included. The same catalogue, with every column and event type, is the "
            + "schemaResource the answer names.")
    @McpOutputSchema(Tables.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult listTables() {
        try (Connection conn = dataSource.getConnection()) {
            List<Table> tables = JfrDatabaseCatalog.relations(conn).stream()
                    .map(relation -> new Table(relation.name(), relation.view()))
                    .toList();
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(DESCRIBE_TABLE).with(TABLE_NAME, JfrDatabaseCatalog.EVENTS_VIEW)
                            .why(DESCRIBE_EVENTS_WHY))
                    .followUp();
            return McpToolResult.of(new Tables(tables, schemaResource(), followUp));
        } catch (SQLException e) {
            LOG.error("Failed to list tables: message={}", e.getMessage(), e);
            throw new ToolExecutionException("Failed to list tables: " + e.getMessage(), e);
        }
    }

    @Tool(description = "Describes one table or view of the JFR profile database: its column names, types "
            + "and nullability. A name the database does not hold is an error.")
    @McpOutputSchema(TableColumns.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult describeTable(
            @ToolParam(required = true, description = "Name of the table or view to describe "
                    + "(e.g., 'events', 'threads', 'frames')")
            String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new ToolExecutionException("Table name is required");
        }

        try (Connection conn = dataSource.getConnection()) {
            List<ProfileSchema.Column> columns = JfrDatabaseCatalog.columns(conn, tableName);
            if (columns.isEmpty()) {
                throw new ToolExecutionException("Table '" + tableName + "' not found");
            }
            boolean events = JfrDatabaseCatalog.EVENTS_VIEW.equals(tableName);
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(EXECUTE_QUERY).with(QUERY, SAMPLE_QUERY.formatted(tableName)).why(SAMPLE_WHY))
                    .nextWhen(events, onProfile(LIST_EVENT_TYPES).why(EVENT_TYPES_WHY))
                    .followUp();
            return McpToolResult.of(new TableColumns(tableName, columns,
                    JfrDatabaseCatalog.note(tableName).orElse(null), schemaResource(), followUp));
        } catch (SQLException e) {
            LOG.error("Failed to describe table: table={} message={}", tableName, e.getMessage(), e);
            throw new ToolExecutionException("Failed to describe table: " + e.getMessage(), e);
        }
    }

    @Tool(description = "Executes a read-only SQL query on the JFR profile database. Only SELECT "
            + "statements are allowed; results are limited to " + MAX_ROWS + " rows and the response size, "
            + "and truncation says which stopped them. The 'events' view holds JFR events with a JSON "
            + "'fields' column for event-specific data. With aggregate functions (COUNT, SUM, AVG, MIN, "
            + "MAX), every non-aggregated column of the SELECT has to appear in the GROUP BY clause. The "
            + "text is a Markdown table; the record carries the columns and each row's cells as text, a "
            + "SQL NULL as null.")
    @McpOutputSchema(QueryResult.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.EXPENSIVE)
    public McpToolResult executeQuery(
            @ToolParam(required = true, description = "SQL SELECT query to execute. Must be a read-only query.")
            String query) {
        if (query == null || query.isBlank()) {
            throw new ToolExecutionException("Query is required");
        }

        // Defence in depth, and deliberately not the boundary. Two things confine this query. The
        // connection: the profile DataSource disables DuckDB's external file access and extension
        // autoloading, so no spelling of a SELECT reaches read_text, glob or ATTACH. And the
        // transaction: readOnly() below runs the statement with autocommit off and rolls it back
        // unconditionally, so a write that satisfies every textual check -- "WITH t AS (SELECT 1)
        // DELETE FROM events ... RETURNING id" starts with WITH, is one statement, and DuckDB runs it
        // through executeQuery -- changes nothing that outlives the call. A prefix test could never
        // do either on its own: it is a string check, not a parser. It stays only to turn an obvious
        // write into a clear message instead of an engine error.
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (!normalizedQuery.startsWith(READ_PREFIX_SELECT) && !normalizedQuery.startsWith(READ_PREFIX_WITH)) {
            throw new ToolExecutionException("Only SELECT and WITH queries are allowed");
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
            throw new ToolExecutionException(MULTIPLE_STATEMENTS_MESSAGE);
        }

        // prepareStatement, not createStatement, for the error messages: Statement.executeQuery
        // reports a missing column, a missing table and a refusal from the sandbox identically, as
        // "unsuccessful or closed pending query result", where a prepared statement carries the
        // engine's real message. That message is the only thing a caller can correct itself from.
        //
        // The row cap is applied while reading, not with setMaxRows: DuckDB's driver accepts that
        // call and ignores it (getMaxRows stays 0). Reading is cheap because results stream, so
        // stopping at the cap does not make the engine materialise the rest.
        try (Connection conn = dataSource.getConnection()) {
            Rows rows = readOnly(conn, () -> {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return Rows.read(rs, MAX_ROWS);
                    }
                }
            });
            return FittingPage.largest(rows.cells().size(),
                            count -> rows.first(count).queryAnswer(this::queryFollowUp), Answer::fits)
                    .orElseGet(() -> rows.none().queryAnswer(this::queryFollowUp))
                    .result();
        } catch (SQLException e) {
            LOG.error("Failed to execute query: query={} message={}", query, e.getMessage(), e);
            throw new ToolExecutionException("Query execution failed: " + e.getMessage(), e);
        }
    }

    private McpFollowUp queryFollowUp(TruncationReason truncation) {
        return NextSteps.builder(advertised)
                .guidanceWhen(truncation == TruncationReason.ROW_LIMIT, ROW_LIMIT_GUIDANCE)
                .guidanceWhen(truncation == TruncationReason.OUTPUT_SIZE_LIMIT, SIZE_LIMIT_GUIDANCE)
                .followUp();
    }

    @Tool(description = "Returns every JFR event type present in this profile, with its count, samples "
            + "and description, the most frequent first.")
    @McpOutputSchema(EventTypes.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult listEventTypes() {
        try (Connection conn = dataSource.getConnection()) {
            List<ProfileSchema.EventType> eventTypes = JfrDatabaseCatalog.eventTypes(conn);
            String busiest = eventTypes.stream()
                    .filter(eventType -> eventType.count() > 0)
                    .map(ProfileSchema.EventType::name)
                    .findFirst()
                    .orElse(null);
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .nextWhen(busiest != null, onProfile(DESCRIBE_EVENT_TYPE).with(EVENT_TYPE, busiest).why(FIELDS_WHY))
                    .nextWhen(busiest != null, onProfile(QUERY_EVENTS).with(EVENT_TYPE, busiest)
                            .with(LIMIT, SAMPLE_EVENTS).why(SAMPLE_EVENTS_WHY))
                    .followUp();
            return McpToolResult.of(new EventTypes(eventTypes, followUp,
                    UiLinks.view(profileId, MicroscopeView.EVENTS)));
        } catch (SQLException e) {
            LOG.error("Failed to list event types: message={}", e.getMessage(), e);
            throw new ToolExecutionException("Failed to list event types: " + e.getMessage(), e);
        }
    }

    @Tool(description = "Queries JFR events of one type with optional filtering, returning timestamps, "
            + "durations, samples and JSON fields newest first (start_timestamp descending), so a "
            + "limit keeps the latest events; truncation says whether more matched. An event type the "
            + "profile never recorded is an error; jfr_listEventTypes names the ones it did. status "
            + "NO_EVENTS: the type holds no event; NO_MATCH: none matched the WHERE clause. The text is "
            + "a Markdown table; the record carries the columns and each row's cells as text, a SQL NULL "
            + "as null.")
    @McpOutputSchema(Events.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult queryEvents(
            @ToolParam(required = true, description = "JFR event type name (e.g., 'jdk.ExecutionSample', 'jdk.GCPhasePause')")
            String eventType,
            @ToolParam(required = false, description = "Maximum number of events to return (default: "
                    + DEFAULT_EVENT_ROWS + ", max: " + MAX_ROWS + ")")
            @ToolParamBounds(defaultValue = DEFAULT_EVENT_ROWS, min = 1, max = MAX_ROWS)
            Integer limit,
            @ToolParam(required = false, description = "Optional SQL WHERE clause for filtering "
                    + "(without the 'WHERE' keyword), using column names exactly as they exist in "
                    + "the events table (e.g. 'duration', not 'duration_ns'). The duration column "
                    + "stores nanoseconds as BIGINT.")
            String whereClause) {

        if (eventType == null || eventType.isBlank()) {
            throw new ToolExecutionException("Event type is required");
        }

        int safeLimit = ToolArguments.boundedLimit(limit, DEFAULT_EVENT_ROWS, MAX_ROWS);

        StringBuilder queryBuilder = new StringBuilder(QUERY_EVENTS_BASE);

        if (whereClause != null && !whereClause.isBlank() && carriesMultipleStatements(whereClause)) {
            throw new ToolExecutionException(MULTIPLE_STATEMENTS_MESSAGE);
        }

        boolean filtered = whereClause != null && !whereClause.isBlank();
        if (filtered) {
            // The fragment is caller-supplied SQL spliced into the statement, and it is confined by
            // the same two things executeQuery is: the connection has no filesystem and no extension
            // loading, and the statement runs inside a transaction that is always rolled back, so the
            // worst a fragment can do is read this profile's own tables -- which is what the tool is
            // for. The keyword denylist that used to stand here was worse than
            // nothing: it missed ATTACH, COPY and every file function, so a scalar subquery walked
            // straight through the AND (...) it lands in, while it rejected honest filters over any
            // value containing "created" or "updated".
            queryBuilder.append(WHERE_FRAGMENT_OPEN).append(whereClause).append(WHERE_FRAGMENT_CLOSE);
        }

        queryBuilder.append(QUERY_EVENTS_ORDER_AND_LIMIT);
        EventQuery request = new EventQuery(eventType, filtered ? whereClause : null, safeLimit,
                UiLinks.view(profileId, MicroscopeView.EVENTS, Map.of(EVENT_TYPE_PARAM, eventType)));

        try (Connection conn = dataSource.getConnection()) {
            return readOnly(conn, () -> {
                try (PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
                    stmt.setString(1, eventType);
                    // One row past the limit, read only to learn whether more matched.
                    stmt.setInt(2, safeLimit + 1);
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    try (ResultSet rs = stmt.executeQuery()) {
                        Rows rows = Rows.read(rs, safeLimit);
                        if (!rows.cells().isEmpty()) {
                            return FittingPage.largest(rows.cells().size(), count -> rows.first(count)
                                                    .eventsAnswer(request, EventsStatus.OK, null, this::eventsFollowUp),
                                            Answer::fits)
                                    .orElseGet(() -> rows.none()
                                            .eventsAnswer(request, EventsStatus.OK, null, this::eventsFollowUp))
                                    .result();
                        }
                        EventsStatus status = noEvents(conn, eventType, filtered);
                        String reason = (status == EventsStatus.NO_MATCH ? NO_EVENTS_MATCHED : NO_EVENTS_OF_TYPE)
                                .formatted(eventType);
                        return rows.eventsAnswer(request, status, reason, this::eventsFollowUp).result();
                    }
                }
            });
        } catch (SQLException e) {
            LOG.error("Failed to query events: eventType={} message={}", eventType, e.getMessage(), e);
            throw new ToolExecutionException("Failed to query events: " + e.getMessage(), e);
        }
    }

    private McpFollowUp eventsFollowUp(EventQuery request, EventsStatus status, TruncationReason truncation) {
        return NextSteps.builder(advertised)
                .nextWhen(status == EventsStatus.OK, onProfile(DESCRIBE_EVENT_TYPE)
                        .with(EVENT_TYPE, request.eventType()).why(FIELDS_WHY))
                .nextWhen(status == EventsStatus.NO_MATCH, onProfile(QUERY_EVENTS)
                        .with(EVENT_TYPE, request.eventType()).with(LIMIT, request.limit()).why(UNFILTERED_WHY))
                .nextWhen(status == EventsStatus.NO_EVENTS, onProfile(LIST_EVENT_TYPES).why(EVENT_TYPES_WHY))
                .guidanceWhen(truncation == TruncationReason.ROW_LIMIT, EVENT_LIMIT_GUIDANCE)
                .guidanceWhen(truncation == TruncationReason.OUTPUT_SIZE_LIMIT, SIZE_LIMIT_GUIDANCE)
                .followUp();
    }

    /**
     * What an empty page of events means. An event type the profile never heard of is the caller's
     * mistake and comes back as one; a type it knows that simply has nothing here is an answer.
     */
    private static EventsStatus noEvents(Connection conn, String eventType, boolean filtered) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(EVENT_TYPE_KNOWN)) {
            stmt.setString(1, eventType);
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(UNKNOWN_EVENT_TYPE.formatted(eventType));
                }
            }
        }
        return filtered ? EventsStatus.NO_MATCH : EventsStatus.NO_EVENTS;
    }

    /**
     * Runs caller-supplied SQL inside a transaction that is rolled back whatever happens, and hands
     * the connection back with autocommit as it found it, since the connection is pooled and the UI
     * reads the same profile through that pool.
     * <p>
     * This is the boundary the prefix check is not. DuckDB accepts a CTE-prefixed DELETE, UPDATE or
     * INSERT through {@code executeQuery} -- with RETURNING it even produces a result set -- and in
     * autocommit mode the row is gone by the time the driver answers. Under an explicit transaction
     * the same statement runs, returns, and is undone before the connection leaves this method. The
     * result set is fully rendered inside the transaction; nothing read here is needed after the
     * rollback.
     */
    private static <T> T readOnly(Connection conn, SqlRead<T> read) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            return read.run();
        } finally {
            try {
                conn.rollback();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @FunctionalInterface
    private interface SqlRead<T> {
        T run() throws SQLException;
    }

    @Tool(description = "Returns the identity of the JFR profile: its profile ID, project ID and "
            + "workspace ID, and whether it belongs to a hub project or to the Quick Analysis store.")
    @McpOutputSchema(ProfileIdentity.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult getProfileInfo() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(PROFILE_INFO)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new ToolExecutionException("No profile information found");
                }
                String projectId = rs.getString("project_id");
                String storedId = rs.getString("profile_id");
                McpFollowUp followUp = NextSteps.builder(advertised)
                        .next(onProfile(PROFILES_SUMMARY).why(SUMMARY_WHY))
                        .followUp();
                return McpToolResult.of(new ProfileIdentity(storedId, projectId, rs.getString("workspace_id"),
                        projectId != null ? ProfileKind.PROJECT : ProfileKind.QUICK_ANALYSIS, followUp,
                        UiLinks.profile(storedId)));
            }
        } catch (SQLException e) {
            LOG.error("Failed to get profile info: message={}", e.getMessage(), e);
            throw new ToolExecutionException("Failed to get profile info: " + e.getMessage(), e);
        }
    }

    private McpNextTool.Call onProfile(String tool) {
        return NextCalls.to(tool).with(PROFILE_ID, profileId);
    }

    /** The schema resource's URI exactly as the resource links attach it, the id encoded the same way. */
    private String schemaResource() {
        return McpResources.schemaUri(profileId);
    }

    /**
     * The rows a query returned, each cell as text and a SQL NULL as null, read while the result set
     * streams: stopped at the row cap or once the cells reach the size cap, whichever comes first,
     * then asked once more whether anything was left unread.
     *
     * @param truncation why the rows stop where they do
     */
    private record Rows(List<String> columns, List<List<String>> cells, TruncationReason truncation) {

        static Rows read(ResultSet rs, int rowCap) throws SQLException {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                columns.add(metaData.getColumnLabel(i + 1));
            }
            List<List<String>> cells = new ArrayList<>();
            long chars = 0;
            while (cells.size() < rowCap && chars < MAX_QUERY_RESULT_LENGTH && rs.next()) {
                List<String> row = new ArrayList<>(columnCount);
                for (int i = 0; i < columnCount; i++) {
                    Object value = rs.getObject(i + 1);
                    String cell = value == null ? null : value.toString();
                    row.add(cell);
                    chars += (cell == null ? NULL_CELL.length() : cell.length()) + CELL_OVERHEAD_CHARS;
                }
                cells.add(Collections.unmodifiableList(row));
            }
            // Asked once more when either cap stopped the loop, so the answer says what is true rather
            // than what is likely: a last row that happens to cross the size cap leaves nothing unread.
            boolean sizeCapReached = chars >= MAX_QUERY_RESULT_LENGTH;
            boolean rowsRemain = (sizeCapReached || cells.size() == rowCap) && rs.next();
            TruncationReason truncation = !rowsRemain
                    ? TruncationReason.COMPLETE
                    : sizeCapReached ? TruncationReason.OUTPUT_SIZE_LIMIT : TruncationReason.ROW_LIMIT;
            return new Rows(List.copyOf(columns), cells, truncation);
        }

        /** The first {@code count} rows; fewer than were read is a cut the response size made. */
        Rows first(int count) {
            if (count == cells.size()) {
                return this;
            }
            return new Rows(columns, cells.subList(0, count), TruncationReason.OUTPUT_SIZE_LIMIT);
        }

        /** No row at all fits, which only a single enormous cell can cause. */
        Rows none() {
            return new Rows(columns, List.of(), cells.isEmpty() ? truncation : TruncationReason.OUTPUT_SIZE_LIMIT);
        }

        Answer queryAnswer(Function<TruncationReason, McpFollowUp> followUps) {
            McpFollowUp followUp = followUps.apply(truncation);
            QueryResult answer = new QueryResult(columns, cells, cells.size(), MAX_ROWS, truncation, followUp);
            return new Answer(table() + LinkedOutput.footer(followUp, null, null), answer);
        }

        Answer eventsAnswer(EventQuery request, EventsStatus status, String reason, EventsFollowUp followUps) {
            McpFollowUp followUp = followUps.of(request, status, truncation);
            Events answer = new Events(status, reason, request.eventType(), request.whereClause(), request.limit(),
                    columns, cells, cells.size(), truncation, followUp, request.uiLink());
            String body = status == EventsStatus.OK ? table() : STATUS_PREFIX + status.name() + LINE_BREAK + reason;
            return new Answer(body + LinkedOutput.footer(followUp, request.uiLink(), null), answer);
        }

        private String table() {
            MarkdownTable table = MarkdownTable.withColumns(columns.toArray(String[]::new));
            for (List<String> row : cells) {
                table.row(row.stream().map(cell -> cell == null ? NULL_CELL : cell).toArray());
            }
            return table.note(cells.size() + " row(s) returned" + switch (truncation) {
                case COMPLETE -> "";
                case ROW_LIMIT -> "; more remain beyond the row cap";
                case OUTPUT_SIZE_LIMIT -> "; more remain beyond the response size limit";
            }).renderUncapped();
        }
    }


    /**
     * An answer measured whole before it is published: rows are left out, never cut, so what the
     * record says it holds is what the table shows.
     */
    private record Answer(String text, Record record) {

        boolean fits() {
            return text.length() <= McpToolOutput.MAX_CHARS && Json.toString(record).length() <= McpToolOutput.MAX_CHARS;
        }

        McpToolResult result() {
            return McpToolResult.of(text, record);
        }
    }

    /** What one events query asked for, carried into the answer and its next calls. */
    private record EventQuery(String eventType, String whereClause, int limit, String uiLink) {
    }

    @FunctionalInterface
    private interface EventsFollowUp {

        McpFollowUp of(EventQuery request, EventsStatus status, TruncationReason truncation);
    }

    /** Whether an events query found anything. */
    enum EventsStatus {
        /** Events are listed. */
        OK,
        /** The profile knows the event type but holds no event of it. */
        NO_EVENTS,
        /** No event of the type matched the WHERE clause. */
        NO_MATCH
    }

    /** Where the profile belongs. */
    enum ProfileKind {
        /** A profile of a hub project's recording. */
        PROJECT,
        /** A profile of a recording in the Quick Analysis store. */
        QUICK_ANALYSIS
    }

    record Table(
            String name,
            @McpDescription("Whether it is a view rather than a stored table")
            boolean view) {
    }

    record Tables(
            List<Table> tables,
            @McpDescription("The resource holding every table and view with its columns, and every event type")
            String schemaResource,
            McpFollowUp followUp) {
    }

    record TableColumns(
            String table,
            List<ProfileSchema.Column> columns,
            @McpNullable
            @McpDescription("What a reader needs to know about the table before querying it; null when nothing")
            String note,
            @McpDescription("The resource holding every table and view with its columns, and every event type")
            String schemaResource,
            McpFollowUp followUp) {
    }

    record QueryResult(
            List<String> columns,
            @McpDescription("One array per row, its cells in column order as text; a SQL NULL is null")
            List<List<@McpNullableElement String>> rows,
            @McpMinimum(0)
            int returned,
            @McpDescription("The most rows any query returns")
            int rowCap,
            @McpDescription("COMPLETE when every row is here; ROW_LIMIT when more rows remain beyond rowCap; "
                    + "OUTPUT_SIZE_LIMIT when more remain beyond the response size limit")
            TruncationReason truncation,
            McpFollowUp followUp) {
    }

    record EventTypes(
            @McpDescription("The most frequent first; a declared type the recording holds none of has count 0")
            List<ProfileSchema.EventType> eventTypes,
            McpFollowUp followUp,
            @McpDescription("The event viewer in the Microscope UI, for the user")
            String uiLink) {
    }

    record Events(
            EventsStatus status,
            @McpNullable
            String reason,
            String eventType,
            @McpNullable
            String whereClause,
            @McpDescription("The most events asked for; the latest are kept")
            int limit,
            List<String> columns,
            @McpDescription("One array per event, newest first, its cells in column order as text; a SQL NULL is null")
            List<List<@McpNullableElement String>> rows,
            @McpMinimum(0)
            int returned,
            @McpDescription("COMPLETE when every matching event is here; ROW_LIMIT when more matched than limit; "
                    + "OUTPUT_SIZE_LIMIT when more remain beyond the response size limit")
            TruncationReason truncation,
            McpFollowUp followUp,
            @McpDescription("The event viewer on this event type in the Microscope UI, for the user")
            String uiLink) {
    }

    record ProfileIdentity(
            String profileId,
            @McpNullable
            @McpDescription("The hub project; null for a Quick Analysis profile")
            String projectId,
            @McpNullable
            @McpDescription("The workspace of that project; null for a Quick Analysis profile")
            String workspaceId,
            ProfileKind kind,
            McpFollowUp followUp,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {
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
