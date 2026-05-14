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

package cafe.jeffrey.profile.heapdump.persistence;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcExecuteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.persistence.GroupLabel;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import tools.jackson.databind.node.ObjectNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JFR-instrumented wrapper around a {@link DuckDBConnection} used by the
 * heap-dump module. Mirrors the {@code DatabaseClient} pattern from
 * {@code shared/persistence} but speaks raw JDBC + DuckDB Appender directly
 * since the heap-dump hot path needs the Appender API.
 *
 * <p><b>Hot-path contract:</b> {@link #withAppender(HeapDumpStatement, String, AppenderBody)}
 * emits exactly <em>one</em> JFR event per appender block. The body owns the
 * per-row {@code beginRow / append / endRow} loop and returns the row count.
 * Wrapping every row would emit tens of millions of events per heap-dump build
 * and erase the perf wins of the appender API.
 *
 * <p>Two instances live per heap dump:
 * <ul>
 *     <li>One on {@code HeapDumpIndexDb} with {@link GroupLabel#HEAP_DUMP_INDEX}
 *     for the write-side index build.</li>
 *     <li>One on {@code DuckDbHeapView} with {@link GroupLabel#HEAP_DUMP_VIEW}
 *     for read-side queries from analyzers and the manager.</li>
 * </ul>
 */
public final class HeapDumpDatabaseClient {

    private static final String APPENDER_SQL_PREFIX = "APPENDER ";

    private static final String DELETE_KEYWORD = "DELETE";

    private static final String UPDATE_KEYWORD = "UPDATE";

    private final DuckDBConnection connection;

    private final String groupLabel;

    public HeapDumpDatabaseClient(DuckDBConnection connection, GroupLabel group) {
        this.connection = connection;
        this.groupLabel = group.name().toLowerCase(Locale.ROOT);
    }

    public DuckDBConnection connection() {
        return connection;
    }

    // ---- Cold path: DDL / PRAGMA / CHECKPOINT / multi-statement schema apply ----

    public void execute(HeapDumpStatement stmt, String sql) {
        JdbcExecuteEvent event = new JdbcExecuteEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();

        try (Statement s = connection.createStatement()) {
            s.execute(sql);
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump execute failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.commit();
            }
        }
    }

    // ---- Cold path: single-statement updates ----

    public int update(HeapDumpStatement stmt, String sql, Object... params) {
        String kw = leadingKeyword(sql);
        if (DELETE_KEYWORD.equals(kw)) {
            return runDelete(stmt, sql, params);
        }
        if (UPDATE_KEYWORD.equals(kw)) {
            return runUpdate(stmt, sql, params);
        }
        return runInsert(stmt, sql, params);
    }

    private int runInsert(HeapDumpStatement stmt, String sql, Object[] params) {
        JdbcInsertEvent event = new JdbcInsertEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        int rows = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            rows = ps.executeUpdate();
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump insert failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return rows;
    }

    private int runUpdate(HeapDumpStatement stmt, String sql, Object[] params) {
        JdbcUpdateEvent event = new JdbcUpdateEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        int rows = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            rows = ps.executeUpdate();
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump update failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return rows;
    }

    private int runDelete(HeapDumpStatement stmt, String sql, Object[] params) {
        JdbcDeleteEvent event = new JdbcDeleteEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        int rows = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            rows = ps.executeUpdate();
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump delete failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return rows;
    }

    // ---- Hot path: appender block as one JFR event ----

    public void withAppender(HeapDumpStatement stmt, String tableName, AppenderBody body) {
        JdbcInsertEvent event = new JdbcInsertEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.isBatch = true;
        event.begin();
        long rows = 0;
        try (DuckDBAppender app = connection.createAppender(tableName)) {
            rows = body.write(app);
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump appender failed: " + stmt + " on " + tableName + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = APPENDER_SQL_PREFIX + tableName;
                event.rows = rows;
                event.commit();
            }
        }
    }

    /**
     * Two-appender variant for phases that interleave writes into two related
     * tables in a single walk (e.g., {@code class + class_instance_field},
     * {@code instance + gc_root}). Emits one combined JFR event covering both
     * appenders; the body returns the total row count across both tables.
     */
    public void withAppenderPair(
            HeapDumpStatement stmt, String primaryTable, String secondaryTable, AppenderPairBody body) {
        JdbcInsertEvent event = new JdbcInsertEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.isBatch = true;
        event.begin();
        long rows = 0;
        try (DuckDBAppender primary = connection.createAppender(primaryTable);
             DuckDBAppender secondary = connection.createAppender(secondaryTable)) {
            rows = body.write(primary, secondary);
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump dual-appender failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = APPENDER_SQL_PREFIX + primaryTable + " + " + secondaryTable;
                event.rows = rows;
                event.commit();
            }
        }
    }

    /**
     * Bulk-loads parquet shards produced by parallel build workers into the
     * given target table via DuckDB's {@code read_parquet} table function.
     * Single statement, single transaction; emits one {@link JdbcInsertEvent}
     * tagged as a batch insert. The {@code parquetGlob} is interpolated
     * directly into the SQL — callers must pass trusted, builder-controlled
     * paths (no user input).
     *
     * @return rows inserted (DuckDB returns the count via {@link Statement#getUpdateCount()})
     */
    public long bulkLoadFromParquet(HeapDumpStatement stmt, String table, String parquetGlob) {
        String sql = "INSERT INTO " + table
                + " SELECT * FROM read_parquet('" + parquetGlob + "')";
        JdbcInsertEvent event = new JdbcInsertEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.isBatch = true;
        event.begin();
        long rows = 0;
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
            rows = s.getLargeUpdateCount();
            if (rows < 0) {
                rows = 0;
            }
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump bulk-load failed: " + stmt + " on " + table + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.commit();
            }
        }
        return rows;
    }

    // ---- Read side ----

    public <T> Optional<T> queryScalar(HeapDumpStatement stmt, String sql, RowMapper<T> mapper, Object... params) {
        JdbcQueryEvent event = new JdbcQueryEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        Optional<T> result = Optional.empty();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result = Optional.ofNullable(mapper.map(rs));
                }
            }
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump scalar query failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = result.isPresent() ? 1 : 0;
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return result;
    }

    public <T> List<T> queryList(HeapDumpStatement stmt, String sql, RowMapper<T> mapper, Object... params) {
        JdbcQueryEvent event = new JdbcQueryEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        List<T> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
            }
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump list query failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = out.size();
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return out;
    }

    public long queryLong(HeapDumpStatement stmt, String sql, Object... params) {
        JdbcQueryEvent event = new JdbcQueryEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.begin();
        long value = 0L;
        boolean present = false;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    value = rs.getLong(1);
                    present = !rs.wasNull();
                }
            }
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump long query failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = present ? 1 : 0;
                event.params = paramsToJson(params);
                event.commit();
            }
        }
        return value;
    }

    public boolean queryExists(HeapDumpStatement stmt, String sql, Object... params) {
        return queryLong(stmt, sql, params) > 0L;
    }

    /**
     * Hot-path escape hatch for bulk scans where row-mapping into Java objects
     * would dominate the actual work (e.g., scanning 30 M outbound_ref edges
     * into primitive {@code int[]} adjacency lists in the dominator-tree
     * build). Emits one {@link JdbcStreamEvent} for the whole scan, identical
     * shape to {@link #queryStream}; the body owns the {@link ResultSet}
     * cursor and returns the row count.
     */
    public void rawStream(HeapDumpStatement stmt, String sql, RawStreamBody body, Object... params) {
        JdbcStreamEvent event = new JdbcStreamEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.sql = sql;
        event.params = paramsToJson(params);
        event.begin();
        long rows = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rows = body.consume(rs);
            }
            event.end();
        } catch (SQLException e) {
            event.isSuccess = false;
            throw new RuntimeException("Heap-dump raw-stream failed: " + stmt + ": " + e.getMessage(), e);
        } finally {
            if (event.shouldCommit()) {
                event.rows = rows;
                event.commit();
            }
        }
    }

    /**
     * Streams query results, bracketing the entire stream lifecycle in a
     * single JFR event that commits on {@link Stream#close()}. Callers
     * <strong>must</strong> close the returned stream (try-with-resources) —
     * otherwise the event never commits and JDBC resources leak.
     *
     * <p>The row counter is incremented as a peek side-effect, so the final
     * count reflects rows actually consumed from the stream (including partial
     * consumption via {@code limit}, {@code findFirst}, etc.).
     */
    public <T> Stream<T> queryStream(HeapDumpStatement stmt, String sql, RowMapper<T> mapper, Object... params) {
        JdbcStreamEvent event = new JdbcStreamEvent(stmt.label(), groupLabel);
        event.isSuccess = true;
        event.sql = sql;
        event.params = paramsToJson(params);
        event.begin();

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection.prepareStatement(sql);
            bind(ps, params);
            rs = ps.executeQuery();
        } catch (SQLException e) {
            closeQuietly(rs);
            closeQuietly(ps);
            event.isSuccess = false;
            event.end();
            if (event.shouldCommit()) {
                event.commit();
            }
            throw new RuntimeException("Heap-dump stream query failed: " + stmt + ": " + e.getMessage(), e);
        }

        LongAdder rowCount = new LongAdder();
        ResultSet capturedRs = rs;
        PreparedStatement capturedPs = ps;

        Spliterator<T> spliterator = new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super T> action) {
                try {
                    if (!capturedRs.next()) {
                        return false;
                    }
                    rowCount.increment();
                    action.accept(mapper.map(capturedRs));
                    return true;
                } catch (SQLException e) {
                    event.isSuccess = false;
                    throw new RuntimeException(e);
                }
            }
        };

        return StreamSupport.stream(spliterator, false).onClose(() -> {
            closeQuietly(capturedRs);
            closeQuietly(capturedPs);
            event.end();
            if (event.shouldCommit()) {
                event.rows = rowCount.longValue();
                event.commit();
            }
        });
    }

    // ---- helpers ----

    @FunctionalInterface
    public interface AppenderBody {
        long write(DuckDBAppender app) throws SQLException;
    }

    @FunctionalInterface
    public interface AppenderPairBody {
        long write(DuckDBAppender primary, DuckDBAppender secondary) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface RawStreamBody {
        long consume(ResultSet rs) throws SQLException;
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private static String leadingKeyword(String sql) {
        int i = 0;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < sql.length() && !Character.isWhitespace(sql.charAt(j))) {
            j++;
        }
        return sql.substring(i, j).toUpperCase(Locale.ROOT);
    }

    private static String paramsToJson(Object[] params) {
        if (params == null || params.length == 0) {
            return null;
        }
        ObjectNode json = Json.createObject();
        for (int i = 0; i < params.length; i++) {
            json.put(Integer.toString(i + 1), params[i] == null ? null : params[i].toString());
        }
        return json.toString();
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
            // suppressed — caller already failed or is closing in onClose
        }
    }
}
