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

package pbouda.jeffrey.provider.writer.sqlite.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfr.types.jdbc.*;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class DatabaseClient {

    private final NamedParameterJdbcOperations delegate;

    public DatabaseClient(DataSource dataSource) {
        this.delegate = new NamedParameterJdbcTemplate(dataSource);
    }

    public int insert(String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        int rows = delegate.update(sql, paramSource);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = rows;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return rows;
    }

    public int insertWithLob(String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        int rows = delegate.update(sql, paramSource);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = rows;
            event.isLob = true;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return rows;
    }

    public void batchInsert(String sql, SqlParameterSource[] paramSources) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        int[] rows = delegate.batchUpdate(sql, paramSources);
        event.end();

        if (event.shouldCommit()) {
            event.rows = sumRows(rows);
            event.isBatch = true;
            // Don't populate `params` and `sql` in batch processing
            // event.sql = sql;
            // event.params = paramSourceToString(paramSource);
            event.commit();
        }
    }

    public int update(String sql, SqlParameterSource paramSource) {
        JdbcUpdateEvent event = new JdbcUpdateEvent();
        event.begin();

        int rows = delegate.update(sql, paramSource);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = rows;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return rows;
    }

    public int delete(String sql, SqlParameterSource paramSource) {
        JdbcDeleteEvent event = new JdbcDeleteEvent();
        event.begin();

        int rows = delegate.update(sql, paramSource);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = rows;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return rows;
    }

    public int delete(String sql) {
        JdbcDeleteEvent event = new JdbcDeleteEvent();
        event.begin();

        int rows = delegate.getJdbcOperations().update(sql);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = rows;
            event.commit();
        }
        return rows;
    }

    public void execute(String sql) {
        JdbcExecuteEvent event = new JdbcExecuteEvent();
        event.begin();

        delegate.getJdbcOperations().execute(sql);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.commit();
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        List<T> list = delegate.query(sql, rowMapper);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = list.size();
            event.commit();
        }
        return list;
    }

    public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        List<T> list = delegate.query(sql, paramSource, rowMapper);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = list.size();
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return list;
    }

    public long queryLong(String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        long longValue = delegate.queryForObject(sql, paramSource, long.class);
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = 1;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return longValue;
    }

    public <T> Optional<T> querySingle(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        List<T> list = query(sql, paramSource, rowMapper);
        Optional<T> result = list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = 1;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return result;
    }

    public boolean queryExists(String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        Long count = delegate.queryForObject(sql, paramSource, Long.class);
        boolean exists = count != null && count > 0;
        event.end();

        if (event.shouldCommit()) {
            event.sql = sql;
            event.rows = 1;
            event.params = paramSourceToJson(paramSource);
            event.commit();
        }
        return exists;
    }

    public <T> Stream<T> queryStream(String sql, RowMapper<T> mapper) {
        Counter counter = new Counter();

        JdbcStreamEvent event = new JdbcStreamEvent();
        // Does not populate `sql` for Streaming
        // event.sql = sql;

        event.begin();
        return delegate.queryForStream(sql, Map.of(), mapper)
                .peek(counter)
                .onClose(new Closer(event, counter));
    }

    private static String paramSourceToJson(SqlParameterSource paramSource) {
        if (paramSource != null
            && paramSource.getParameterNames() != null
            && paramSource.getParameterNames().length > 0) {

            ObjectNode json = Json.createObject();
            for (String paramName : paramSource.getParameterNames()) {
                json.put(paramName, resolveParamValue(paramSource.getValue(paramName)));
            }
            return json.toString();
        } else {
            return null;
        }
    }

    private static String resolveParamValue(Object value) {
        if (value instanceof SqlLobValue) {
            return "<lob-value>";
        } else {
            return value.toString();
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
}
