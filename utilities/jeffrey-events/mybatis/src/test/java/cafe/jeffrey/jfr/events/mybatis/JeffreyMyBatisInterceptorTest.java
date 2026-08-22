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

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.consumer.RecordedEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Driven through a real MyBatis {@code SqlSession} over a real database: the value of this module
 * over the {@code DataSource} proxy is that MyBatis knows the statement id, and only MyBatis
 * itself can hand that over.
 */
class JeffreyMyBatisInterceptorTest {

    private static final String SELECT_NAME = "UserMapper.findName";
    private static final String INSERT_NAME = "UserMapper.insert";
    private static final String GROUP = "UserMapper";

    private DuckDBConnection connection;
    private SqlSessionFactory sessionFactory;

    /** Mapper methods, whose ids become the recorded span names. */
    public interface UserMapper {

        @Select("SELECT name FROM users WHERE id = #{id}")
        String findName(@Param("id") int id);

        @Insert("INSERT INTO users VALUES (#{id}, #{name})")
        int insert(@Param("id") int id, @Param("name") String name);
    }

    @BeforeEach
    void setUp() throws SQLException {
        connection = (DuckDBConnection) new DuckDBDriver().connect("jdbc:duckdb:", new Properties());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
            statement.execute("INSERT INTO users VALUES (1, 'ada')");
        }

        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), new SharedConnectionDataSource(connection)));
        configuration.addMapper(UserMapper.class);
        configuration.addInterceptor(new JeffreyMyBatisInterceptor());
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    @DisplayName("a query is named by its mapper method, not by its SQL")
    void queryIsNamedByStatementId() throws IOException {
        RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                assertEquals("ada", inSession(mapper -> mapper.findName(1))));

        assertEquals(SELECT_NAME, event.getString("name"),
                "the statement id is the one name that is stable however the SQL is assembled");
        assertEquals(GROUP, event.getString("group"));
        assertEquals(SpanKind.CLIENT.name(), event.getString("kind"));
        assertEquals(1, event.getLong("rows"));
    }

    @Test
    @DisplayName("the SQL keeps its placeholders, so identical statements aggregate")
    void sqlKeepsPlaceholders() throws IOException {
        RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                inSession(mapper -> mapper.findName(1)));

        assertEquals("SELECT name FROM users WHERE id = ?", event.getString("sql").trim());
    }

    @Test
    @DisplayName("the command type picks the event type, and affected rows are recorded")
    void insertPicksItsEventType() throws IOException {
        RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () ->
                inSession(mapper -> mapper.insert(2, "grace")));

        assertEquals(INSERT_NAME, event.getString("name"));
        assertEquals(1, event.getLong("rows"));
    }

    @Test
    @DisplayName("a statement nests under the span in progress")
    void statementNestsUnderTheSpan() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(
                List.of("jeffrey.TraceSpan", JdbcQueryEvent.NAME),
                () -> Tracer.run("users.show", SpanKind.SERVER, () -> inSession(mapper -> mapper.findName(1))));

        SpansAssert.assertThat(events)
                .hasNoUntracedSpans()
                .hasNoOrphanedSpans()
                .hasSpan(SELECT_NAME).nestedUnder("users.show");
    }

    private <T> T inSession(Function<UserMapper, T> body) {
        try (SqlSession session = sessionFactory.openSession()) {
            return body.apply(session.getMapper(UserMapper.class));
        }
    }

    /** Hands out duplicates of one in-memory DuckDB connection. */
    private record SharedConnectionDataSource(DuckDBConnection connection) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return connection.duplicate();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return iface.cast(this);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
