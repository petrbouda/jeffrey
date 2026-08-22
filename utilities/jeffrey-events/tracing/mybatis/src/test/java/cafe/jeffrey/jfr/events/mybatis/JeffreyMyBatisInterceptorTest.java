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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    private DataSource dataSource;

    /** Mapper methods, whose ids become the recorded span names. */
    public interface UserMapper {

        @Select("SELECT name FROM users WHERE id = #{id}")
        String findName(@Param("id") int id);

        @Insert("INSERT INTO users VALUES (#{id}, #{name})")
        int insert(@Param("id") int id, @Param("name") String name);

        @Select("SELECT name FROM users WHERE id = #{id} OR id = #{id}")
        String findNameTwice(@Param("id") int id);

        @Select("SELECT name FROM users WHERE name = #{name} OR name = #{missing,jdbcType=VARCHAR}")
        String findByName(@Param("name") String name, @Param("missing") String missing);

        @Select("SELECT count(*) FROM users WHERE name = CAST(#{payload} AS VARCHAR)")
        int countByPayload(@Param("payload") byte[] payload);

        @Select("SELECT count(*) FROM users")
        int countAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        connection = (DuckDBConnection) new DuckDBDriver().connect("jdbc:duckdb:", new Properties());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
            statement.execute("INSERT INTO users VALUES (1, 'ada')");
            statement.execute("CREATE TABLE blobs (data BLOB)");
        }

        dataSource = new SharedConnectionDataSource(connection);
        sessionFactory = sessionFactoryWith(new JeffreyMyBatisInterceptor());
    }

    private SqlSessionFactory sessionFactoryWith(JeffreyMyBatisInterceptor interceptor) {
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(UserMapper.class);
        configuration.addInterceptor(interceptor);
        return new SqlSessionFactoryBuilder().build(configuration);
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

    /**
     * The values a statement ran with are what separates the one slow call from the thousands that
     * share its SQL, so they are recorded — and everything here is about recording them safely.
     */
    @Nested
    @DisplayName("The parameters a statement ran with")
    class Parameters {

        @Test
        @DisplayName("are keyed by the names the mapper method gave them")
        void namedParametersAreRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    inSession(mapper -> mapper.findName(1)));

            assertEquals("{\"id\":1}", event.getString("params"),
                    "a number is written as a number, not as a quoted string");
        }

        @Test
        @DisplayName("are escaped, so a value carrying a quote cannot corrupt the field")
        void valuesAreEscaped() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () ->
                    inSession(mapper -> mapper.insert(3, "gr\"ace\\")));

            assertEquals("{\"id\":3,\"name\":\"gr\\\"ace\\\\\"}", event.getString("params"));
        }

        @Test
        @DisplayName("record a null argument as null rather than dropping it")
        void nullArgumentIsRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    inSession(mapper -> mapper.findByName("ada", null)));

            assertEquals("{\"name\":\"ada\",\"missing\":null}", event.getString("params"));
        }

        @Test
        @DisplayName("write a property used twice once, because both placeholders resolve alike")
        void repeatedPropertyIsWrittenOnce() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    inSession(mapper -> mapper.findNameTwice(1)));

            assertEquals("{\"id\":1}", event.getString("params"));
        }

        @Test
        @DisplayName("are truncated, so one oversized argument cannot bloat every recording")
        void longValuesAreTruncated() throws IOException {
            SqlSessionFactory factory = sessionFactoryWith(
                    new JeffreyMyBatisInterceptor(new MyBatisStatementSettings(true, 4)));

            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () ->
                    inSession(factory, mapper -> mapper.insert(4, "abcdefgh")));

            assertEquals("{\"id\":4,\"name\":\"abcd…\"}", event.getString("params"));
        }

        @Test
        @DisplayName("stand in for binary content instead of rendering it")
        void binaryContentIsNotRendered() throws IOException {
            byte[] payload = "not text".getBytes(StandardCharsets.UTF_8);

            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    assertEquals(0, (int) inSession(mapper -> mapper.countByPayload(payload)),
                            "the statement still runs — rendering a stream would have consumed it"));

            assertEquals("{\"payload\":\"<lob-value>\"}", event.getString("params"));
        }

        @Test
        @DisplayName("are absent for a statement that takes none")
        void statementWithoutParametersRecordsNone() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    inSession(UserMapper::countAll));

            assertNull(event.getString("params"), "an empty object would only be noise");
        }

        @Test
        @DisplayName("can be turned off for an application whose arguments are sensitive")
        void captureCanBeTurnedOff() throws IOException {
            SqlSessionFactory factory = sessionFactoryWith(
                    new JeffreyMyBatisInterceptor(MyBatisStatementSettings.noParameters()));

            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    inSession(factory, mapper -> mapper.findName(1)));

            assertNull(event.getString("params"));
            assertEquals(SELECT_NAME, event.getString("name"), "the statement itself is still recorded");
        }

        @Test
        @DisplayName("are configurable through MyBatis' own plugin properties")
        void pluginPropertiesConfigureTheSettings() {
            Properties properties = new Properties();
            properties.setProperty("capture-parameters", "false");
            properties.setProperty("max-value-length", "16");

            JeffreyMyBatisInterceptor interceptor = new JeffreyMyBatisInterceptor();
            interceptor.setProperties(properties);

            assertEquals(new MyBatisStatementSettings(false, 16), interceptor.settings());
        }

        @Test
        @DisplayName("keep their defaults when a plugin declaration mentions neither")
        void unmentionedPropertiesKeepTheirDefaults() {
            JeffreyMyBatisInterceptor interceptor = new JeffreyMyBatisInterceptor();
            interceptor.setProperties(new Properties());

            assertEquals(MyBatisStatementSettings.defaults(), interceptor.settings());
        }
    }

    private <T> T inSession(Function<UserMapper, T> body) {
        return inSession(sessionFactory, body);
    }

    private <T> T inSession(SqlSessionFactory factory, Function<UserMapper, T> body) {
        try (SqlSession session = factory.openSession()) {
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
