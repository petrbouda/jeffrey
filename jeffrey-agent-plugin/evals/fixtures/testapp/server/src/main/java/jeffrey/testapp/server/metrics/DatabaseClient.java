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

package jeffrey.testapp.server.metrics;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcExecuteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.SqlBinaryValue;
import org.springframework.jdbc.support.KeyHolder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Records every statement as a Jeffrey JDBC event, named by its {@link StatementLabel} and grouped
 * under the repository that issued it — the same shape as Jeffrey's own {@code DatabaseClient}.
 * <p>
 * Every statement goes through {@link TracedEvents#emit}, which is the whole leaf lifecycle: the
 * zero-cost passthrough when nothing is recording, the interval, {@code failed(e)} on the exception
 * path, and {@code commitSpan()} — which nests the statement under the span in progress (the HTTP
 * request's root span, or a {@code Tracer} span in between). The fillers run only when the event is
 * actually committed.
 * <p>
 * This replaces the starter's {@code DataSource} wrapper ({@code jeffrey.tracing.jdbc-enabled=false}
 * in application.properties): the wrapper names statements by verb and table and does not count
 * returned rows, while this client records the label, the bound parameters and the row counts.
 */
public class DatabaseClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String LOB_VALUE = "<lob-value>";

    private final NamedParameterJdbcTemplate delegate;
    private final String groupLabel;

    public DatabaseClient(NamedParameterJdbcTemplate delegate, String groupLabel) {
        this.delegate = delegate;
        this.groupLabel = groupLabel;
    }

    public int insert(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int insertWithLob(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.isLob = true;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public long batchInsert(StatementLabel statement, String sql, SqlParameterSource[] paramSources) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> sumRows(delegate.batchUpdate(sql, paramSources)),
                (e, rows) -> {
                    e.rows = rows != null ? rows : 0;
                    e.isBatch = true;
                    // Don't populate `params` and `sql` in batch processing
                });
    }

    public int update(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcUpdateEvent event = new JdbcUpdateEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int update(
            StatementLabel statement, String sql, SqlParameterSource paramSource, KeyHolder generatedKeyHolder) {

        JdbcUpdateEvent event = new JdbcUpdateEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource, generatedKeyHolder),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int delete(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcDeleteEvent event = new JdbcDeleteEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int delete(StatementLabel statement, String sql) {
        JdbcDeleteEvent event = new JdbcDeleteEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.getJdbcOperations().update(sql),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                });
    }

    public void execute(StatementLabel statement, String sql) {
        JdbcExecuteEvent event = new JdbcExecuteEvent(statement.label(), groupLabel);
        TracedEvents.emit(event,
                () -> delegate.getJdbcOperations().execute(sql),
                e -> e.sql = sql);
    }

    public <T> List<T> query(StatementLabel statement, String sql, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.query(sql, rowMapper),
                (e, list) -> {
                    e.sql = sql;
                    e.rows = list != null ? list.size() : 0;
                });
    }

    public <T> List<T> query(
            StatementLabel statement, String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {

        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.query(sql, paramSource, rowMapper),
                (e, list) -> {
                    e.sql = sql;
                    e.rows = list != null ? list.size() : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public long queryLong(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        Long value = TracedEvents.emit(event,
                () -> delegate.queryForObject(sql, paramSource, Long.class),
                (e, result) -> {
                    e.sql = sql;
                    e.rows = result != null ? 1 : 0;
                    e.params = paramSourceToJson(paramSource);
                });
        return value != null ? value : 0L;
    }

    public <T> Optional<T> querySingle(
            StatementLabel statement, String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {

        List<T> list = query(statement, sql, paramSource, rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public boolean queryExists(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> {
                    Long count = delegate.queryForObject(sql, paramSource, Long.class);
                    return count != null && count > 0;
                },
                (e, exists) -> {
                    e.sql = sql;
                    e.rows = Boolean.TRUE.equals(exists) ? 1 : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    /**
     * Streams the statement's rows into {@code consumer} without materializing them, and records
     * the rows that actually flowed through as the event's row count — right whether the stream ran
     * to the end or a failure cut it short.
     */
    public <T> void queryStream(StatementLabel statement, String sql, RowMapper<T> mapper, Consumer<T> consumer) {
        CountingConsumer<T> countingConsumer = new CountingConsumer<>(consumer);

        JdbcStreamEvent event = new JdbcStreamEvent(statement.label(), groupLabel);
        TracedEvents.emit(event,
                () -> {
                    try (Stream<T> stream = delegate.queryForStream(sql, Map.of(), mapper)) {
                        stream.forEach(countingConsumer);
                    }
                },
                e -> {
                    e.sql = sql;
                    e.rows = countingConsumer.rows();
                });
    }

    private static String paramSourceToJson(SqlParameterSource paramSource) {
        if (paramSource != null
                && paramSource.getParameterNames() != null
                && paramSource.getParameterNames().length > 0) {

            ObjectNode json = MAPPER.createObjectNode();
            for (String paramName : paramSource.getParameterNames()) {
                json.put(paramName, resolveParamValue(paramSource.getValue(paramName)));
            }
            return json.toString();
        } else {
            return null;
        }
    }

    private static String resolveParamValue(Object value) {
        if (value instanceof SqlBinaryValue) {
            return LOB_VALUE;
        } else {
            return value == null ? null : value.toString();
        }
    }

    private static long sumRows(int[] updateCount) {
        long count = 0L;
        for (int i : updateCount) {
            if (i == Statement.SUCCESS_NO_INFO) {
                return Statement.SUCCESS_NO_INFO;
            }
            count += i;
        }
        return count;
    }

    private static final class CountingConsumer<T> implements Consumer<T> {

        private final Consumer<T> delegate;
        private long rows;

        private CountingConsumer(Consumer<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(T row) {
            rows++;
            delegate.accept(row);
        }

        private long rows() {
            return rows;
        }
    }

    public enum StatementLabel {
        FIND_BY_ID,
        FIND_ALL_BY_IDS,
        ALL_QUERY,
        ALL_IDS_QUERY,
        COUNT_QUERY,
        REMOVE_QUERY_BY_ID,
        REMOVE_ALL,
        INSERT_PERSON,
        RAW_INSERT,
        BACKUP_DUMP,
        BACKUP_LOAD;

        private final String label = name().toLowerCase();

        public String label() {
            return label;
        }
    }
}
