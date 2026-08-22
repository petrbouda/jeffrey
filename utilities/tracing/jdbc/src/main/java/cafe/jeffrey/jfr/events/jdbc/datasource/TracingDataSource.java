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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A {@link DataSource} that records every statement run through it as a Jeffrey JDBC event.
 * <p>
 * Wrapping the {@code DataSource} rather than instrumenting a persistence framework is what makes
 * this one module cover JdbcTemplate, Hibernate/JPA, jOOQ and MyBatis at once: they all reach the
 * database through this interface, so instrumenting it catches the statements each of them issues,
 * including the ones an ORM generates that nobody wrote by hand.
 * <p>
 * Each statement becomes a <b>leaf span</b> nested under whatever span is in progress on the
 * executing thread — the request being served, typically — or an untraced-but-recorded event when
 * there is none.
 *
 * <pre>{@code
 * DataSource traced = new TracingDataSource(hikariDataSource, "orders-db");
 * }</pre>
 *
 * <h2>What it does not do</h2>
 * Statement <em>parameters</em> are never read, let alone recorded: they are the values most
 * likely to be personal data, and a proxy cannot know which are safe. The SQL text is recorded as
 * the driver received it, so a statement built with placeholders stays aggregatable and a
 * statement built by string concatenation carries whatever was concatenated into it.
 */
public class TracingDataSource implements DataSource {

    private final DataSource delegate;
    private final StatementNaming naming;
    private final String group;

    /**
     * @param delegate the real data source
     * @param group    the label statements are grouped under in Jeffrey's Database dashboard;
     *                 usually the database or pool name
     * @param naming   what each statement is called
     */
    public TracingDataSource(DataSource delegate, String group, StatementNaming naming) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.naming = Objects.requireNonNull(naming, "naming must not be null");
    }

    /**
     * The form that names statements by verb and primary table.
     */
    public TracingDataSource(DataSource delegate, String group) {
        this(delegate, group, StatementNaming.verbAndTable());
    }

    /**
     * @return the data source this one wraps, for code that needs the untraced original
     */
    public DataSource delegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return StatementTracing.wrapConnection(delegate.getConnection(), naming, group);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return StatementTracing.wrapConnection(delegate.getConnection(username, password), naming, group);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    /**
     * Unwraps to the delegate, so code reaching for a vendor type — a {@code HikariDataSource} to
     * read its pool, say — still finds it through the proxy.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}
