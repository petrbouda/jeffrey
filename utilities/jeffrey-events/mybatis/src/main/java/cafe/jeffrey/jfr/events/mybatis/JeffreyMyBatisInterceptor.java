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

package cafe.jeffrey.jfr.events.mybatis;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcBaseEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStatementEvents;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

/**
 * Records every MyBatis statement as a Jeffrey leaf span, named by the mapper method that issued
 * it.
 * <p>
 * MyBatis is worth instrumenting directly even though
 * {@code jeffrey-events-jdbc} would already catch its statements through the {@code DataSource},
 * because MyBatis knows something the driver does not: the <b>statement id</b>. A
 * {@code DataSource} proxy has to name a statement by reading its SQL, while this names it
 * {@code UserMapper.selectById} — one name per mapper method, stable however the SQL is
 * assembled, and the same name a developer would search for. Use one or the other, not both.
 * <p>
 * The {@code Executor} is the interception point rather than {@code StatementHandler} so that
 * cached and batched execution are covered too.
 *
 * <h2>Registration</h2>
 * On mybatis-spring-boot-starter, declaring it as a bean is enough — every {@code Interceptor}
 * bean is added to the {@code SqlSessionFactory}. Otherwise
 * {@code configuration.addInterceptor(new JeffreyMyBatisInterceptor())}, or the {@code <plugins>}
 * element in XML. Register it through exactly one of those: registered twice, it records every
 * statement twice.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
public class JeffreyMyBatisInterceptor implements Interceptor {

    private static final char ID_SEPARATOR = '.';
    private static final char NESTED_CLASS_SEPARATOR = '$';
    private static final int STATEMENT_ARGUMENT = 0;
    private static final int PARAMETER_ARGUMENT = 1;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[STATEMENT_ARGUMENT];
        Object parameter = invocation.getArgs()[PARAMETER_ARGUMENT];

        // emit applies the whole lifecycle: the guard when nothing is recording, the interval,
        // failed(t) on a throw, and the stamping commit that nests this under the request's span.
        return TracedEvents.emit(createEvent(statement),
                invocation::proceed,
                (event, result) -> {
                    event.sql = statement.getBoundSql(parameter).getSql();
                    event.rows = countRows(result);
                });
    }

    /**
     * Names the statement {@code UserMapper.selectById} and groups it under {@code UserMapper}.
     * <p>
     * The statement id is fully qualified ({@code com.example.mapper.UserMapper.selectById}); the
     * package is dropped because it adds length to every recorded name without distinguishing
     * anything a reader cares about. A mapper declared as a nested interface arrives as
     * {@code Outer$UserMapper}, and the enclosing class is dropped for the same reason — the
     * recorded name reads the same whether a mapper is nested or not.
     */
    private static JdbcBaseEvent createEvent(MappedStatement statement) {
        String verb = statement.getSqlCommandType().name();
        String id = statement.getId();

        int methodSeparator = id.lastIndexOf(ID_SEPARATOR);
        if (methodSeparator < 0) {
            return JdbcStatementEvents.forVerb(verb, id, null);
        }

        String mapper = simpleMapperName(id.substring(0, methodSeparator));
        String method = id.substring(methodSeparator + 1);

        // The verb-to-event-type mapping is the library's own convention; forVerb applies it.
        return JdbcStatementEvents.forVerb(verb, mapper + ID_SEPARATOR + method, mapper);
    }

    /**
     * The mapper's own name, without its package or any enclosing class.
     */
    private static String simpleMapperName(String qualifiedMapper) {
        int lastSeparator = Math.max(
                qualifiedMapper.lastIndexOf(ID_SEPARATOR), qualifiedMapper.lastIndexOf(NESTED_CLASS_SEPARATOR));
        return qualifiedMapper.substring(lastSeparator + 1);
    }

    /**
     * Rows returned for a query, rows affected for everything else — which is what MyBatis hands
     * back in each case.
     */
    private static long countRows(Object result) {
        return switch (result) {
            case List<?> rows -> rows.size();
            case Integer affected -> affected;
            case null, default -> 0;
        };
    }
}
