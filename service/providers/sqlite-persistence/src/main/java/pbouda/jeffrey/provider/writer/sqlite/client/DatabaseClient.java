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
import pbouda.jeffrey.jfr.types.jdbc.statement.*;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DatabaseClient {

    private final NamedParameterJdbcOperations delegate;

    private final String groupLabel;

    public DatabaseClient(DataSource dataSource, String groupLabel) {
        this.delegate = new NamedParameterJdbcTemplate(dataSource);
        this.groupLabel = groupLabel;
    }

    public int insert(String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        int rows = 0;
        try {
            rows = delegate.update(sql, paramSource);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return rows;
    }

    public int insertWithLob(String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        int rows = 0;
        try {
            rows = delegate.update(sql, paramSource);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.isLob = true;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return rows;
    }

    public long batchInsert(String sql, SqlParameterSource[] paramSources) {
        JdbcInsertEvent event = new JdbcInsertEvent();
        event.begin();

        long rowsSum = 0;
        try {
            int[] rows = delegate.batchUpdate(sql, paramSources);
            event.end();
            rowsSum = sumRows(rows);
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.rows = rowsSum;
                event.isBatch = true;
                // Don't populate `params` and `sql` in batch processing
                // event.sql = sql;
                // event.params = paramSourceToString(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return rowsSum;
    }

    public int update(String sql, SqlParameterSource paramSource) {
        JdbcUpdateEvent event = new JdbcUpdateEvent();
        event.begin();

        int rows = 0;
        try {
            rows = delegate.update(sql, paramSource);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return rows;
    }

    public int delete(String sql, SqlParameterSource paramSource) {
        JdbcDeleteEvent event = new JdbcDeleteEvent();
        event.begin();

        int rows = 0;
        try {
            rows = delegate.update(sql, paramSource);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return rows;
    }

    public int delete(String sql) {
        JdbcDeleteEvent event = new JdbcDeleteEvent();
        event.begin();

        int rows = 0;
        try {
            rows = delegate.getJdbcOperations().update(sql);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = rows;
                event.group = groupLabel;
                event.commit();
            }
        }

        return rows;
    }

    public void execute(String sql) {
        JdbcExecuteEvent event = new JdbcExecuteEvent();
        event.begin();

        try {
            delegate.getJdbcOperations().execute(sql);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.group = groupLabel;
                event.commit();
            }
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        List<T> list = null;
        try {
            list = delegate.query(sql, rowMapper);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = list != null ? list.size() : 0;
                event.group = groupLabel;
                event.commit();
            }
        }

        return list;
    }

    public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        List<T> list = null;
        try {
            list = delegate.query(sql, paramSource, rowMapper);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = list != null ? list.size() : 0;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return list;
    }

    public long queryLong(String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        Long longValue = null;
        try {
            longValue = delegate.queryForObject(sql, paramSource, long.class);
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = longValue != null ? 1 : 0;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }
        return longValue;
    }

    public <T> Optional<T> querySingle(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        Optional<T> result = Optional.empty();
        try {
            List<T> list = query(sql, paramSource, rowMapper);
            result = list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = result.isPresent() ? 1 : 0;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return result;
    }

    public boolean queryExists(String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent();
        event.begin();

        boolean exists = false;
        try {
            Long count = delegate.queryForObject(sql, paramSource, Long.class);
            exists = count != null && count > 0;
            event.end();
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            if (event.shouldCommit()) {
                event.sql = sql;
                event.rows = exists ? 1 : 0;
                event.params = paramSourceToJson(paramSource);
                event.group = groupLabel;
                event.commit();
            }
        }

        return exists;
    }

    public <T> void queryStream(String sql, RowMapper<T> mapper, Consumer<T> consumer) {
        Counter counter = new Counter();

        JdbcStreamEvent event = new JdbcStreamEvent();
        event.sql = sql;
        event.begin();

        try (Stream<T> queryStream = delegate.queryForStream(sql, Map.of(), mapper)) {
            queryStream.peek(counter).forEach(consumer);
        } catch (Exception e) {
            event.isSuccess = false;
            throw e;
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.group = groupLabel;
                event.rows = counter.rows();
                event.samples = counter.samples();
                event.commit();
            }
        }
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
}
