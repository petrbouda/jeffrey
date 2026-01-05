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
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class CachedDataSource implements DataSource, AutoCloseable {

    private final DataSource delegate;
    private final Clock clock;
    private final AtomicReference<Instant> lastAccessed;
    private final boolean suppressClose;

    public CachedDataSource(DataSource delegate, Clock clock) {
        this(delegate, clock, true);
    }

    public CachedDataSource(DataSource delegate, Clock clock, boolean suppressClose) {
        this.delegate = delegate;
        this.clock = clock;
        this.lastAccessed = new AtomicReference<>(clock.instant());
        this.suppressClose = suppressClose;
    }

    public Instant lastAccessed() {
        return lastAccessed.get();
    }

    public DataSource delegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        lastAccessed.set(clock.instant());
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        lastAccessed.set(clock.instant());
        return delegate.getConnection(username, password);
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

    @Override
    public void close() {
        if (!suppressClose) {
            forceClose();
        }
    }

    public void forceClose() {
        if (delegate != null) {
            DataSourceUtils.close(delegate);
        }
    }
}
