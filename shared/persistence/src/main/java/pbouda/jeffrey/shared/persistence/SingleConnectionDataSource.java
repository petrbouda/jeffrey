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

package pbouda.jeffrey.shared.persistence;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * A DataSource implementation that caches and reuses a single connection.
 * Useful for scenarios where a single connection should be shared across multiple consumers,
 * such as DuckDB write operations where multiple concurrent connections cause issues.
 * <p>
 * The connection returned by {@link #getConnection()} is wrapped to ignore {@code close()} calls,
 * preventing consumers from accidentally closing the shared connection.
 */
public class SingleConnectionDataSource implements DataSource, AutoCloseable {

    private final Connection connection;
    private final Connection nonClosingWrapper;
    private PrintWriter logWriter;

    public SingleConnectionDataSource(String url) {
        try {
            this.connection = DriverManager.getConnection(url);
            this.nonClosingWrapper = new NonClosingConnectionWrapper(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create connection: url=" + url, e);
        }
    }

    @Override
    public Connection getConnection() {
        return nonClosingWrapper;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return nonClosingWrapper;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
