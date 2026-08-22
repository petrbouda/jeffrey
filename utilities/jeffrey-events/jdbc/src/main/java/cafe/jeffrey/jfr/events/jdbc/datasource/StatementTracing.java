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

package cafe.jeffrey.jfr.events.jdbc.datasource;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcBaseEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStatementEvents;
import cafe.jeffrey.jfr.events.trace.TracedEvents;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Set;

/**
 * Wraps a {@link Connection} so that every statement it runs commits a Jeffrey JDBC event.
 * <p>
 * Dynamic proxies rather than hand-written delegates: {@link Connection} and
 * {@link PreparedStatement} declare well over a hundred methods between them, almost none of which
 * this instrumentation cares about, and delegating them by hand would be a large surface for a
 * typo to hide in — one forwarded method quietly returning the wrong thing is a bug in the
 * application, not just in its telemetry. Only the handful of methods named below are intercepted;
 * everything else passes straight through.
 * <p>
 * {@link java.sql.ResultSet} is deliberately not wrapped. Counting returned rows would mean
 * proxying every {@code next()} call on the hot path of every query, which costs more than the
 * number is worth; a query's {@code rows} therefore stays {@code 0}, while updates and batches —
 * where the driver hands the count back directly — record it.
 */
final class StatementTracing {

    /** Methods on {@link Connection} that hand back a statement carrying its own SQL. */
    private static final Set<String> PREPARING_METHODS = Set.of("prepareStatement", "prepareCall");

    private static final String CREATE_STATEMENT = "createStatement";
    private static final String EXECUTE_PREFIX = "execute";

    private StatementTracing() {
    }

    static Connection wrapConnection(Connection connection, StatementNaming naming, String group) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(connection, naming, group));
    }

    private record ConnectionHandler(Connection delegate, StatementNaming naming, String group)
            implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = call(delegate, method, args);

            if (!(result instanceof Statement statement)) {
                return result;
            }
            // A prepared statement knows its SQL now; a plain one is handed it at execute time.
            String sql = PREPARING_METHODS.contains(method.getName()) && args != null && args.length > 0
                    ? String.valueOf(args[0])
                    : null;
            if (sql != null || CREATE_STATEMENT.equals(method.getName())) {
                return wrapStatement(statement, sql, naming, group);
            }
            return result;
        }
    }

    private static Statement wrapStatement(
            Statement statement, String preparedSql, StatementNaming naming, String group) {

        Class<?> contract = contractOf(statement);
        return (Statement) Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[]{contract},
                new StatementHandler(statement, preparedSql, naming, group));
    }

    /**
     * The most specific JDBC interface the statement implements, so a caller that prepared a
     * {@link CallableStatement} still gets one back.
     */
    private static Class<?> contractOf(Statement statement) {
        if (statement instanceof CallableStatement) {
            return CallableStatement.class;
        }
        if (statement instanceof PreparedStatement) {
            return PreparedStatement.class;
        }
        return Statement.class;
    }

    private record StatementHandler(Statement delegate, String preparedSql, StatementNaming naming, String group)
            implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!method.getName().startsWith(EXECUTE_PREFIX)) {
                return call(delegate, method, args);
            }

            String sql = resolveSql(method, args);
            JdbcBaseEvent event = JdbcStatementEvents.forSql(sql, naming.name(sql), group);

            return TracedEvents.emit(event,
                    () -> call(delegate, method, args),
                    (emitted, result) -> {
                        emitted.sql = sql;
                        emitted.rows = rowsOf(result);
                    });
        }

        /**
         * A batch has no single SQL of its own, and {@code Statement.execute(sql)} carries it as
         * the first argument; a prepared statement always uses the SQL it was prepared with.
         */
        private String resolveSql(Method method, Object[] args) {
            if (preparedSql != null) {
                return preparedSql;
            }
            if (args != null && args.length > 0 && args[0] instanceof String sql) {
                return sql;
            }
            return "";
        }
    }

    /**
     * The row count the driver handed back, where it hands one back at all.
     */
    private static long rowsOf(Object result) {
        return switch (result) {
            case Integer updated -> updated;
            case Long updated -> updated;
            case int[] batch -> sum(batch);
            case long[] batch -> sum(batch);
            // A ResultSet (executeQuery) or a Boolean (execute): the count is not knowable without
            // wrapping the ResultSet, which is not worth the hot-path cost.
            case null, default -> 0;
        };
    }

    private static long sum(int[] counts) {
        long total = 0;
        for (int count : counts) {
            // SUCCESS_NO_INFO / EXECUTE_FAILED are negative markers, not counts.
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    private static long sum(long[] counts) {
        long total = 0;
        for (long count : counts) {
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    /**
     * Invokes the delegate, unwrapping the reflection layer so the caller sees the driver's own
     * {@link java.sql.SQLException} and the event records the real failure rather than an
     * {@link InvocationTargetException}.
     */
    private static Object call(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
