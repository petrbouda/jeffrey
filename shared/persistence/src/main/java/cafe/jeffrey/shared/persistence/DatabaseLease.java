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

package cafe.jeffrey.shared.persistence;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A scoped hold on a cached {@link DataSource}. While the lease is open, the owning
 * {@link DatabaseManager} must keep the underlying pool alive (it must not be idle-evicted),
 * so a long-running writer (e.g. profile initialization) cannot have its connections closed
 * out from under it. The pool is released back to normal idle-eviction when the lease is closed.
 * <p>
 * Closing the lease never closes the pool itself — the pool's lifecycle is owned by the manager.
 */
public final class DatabaseLease implements AutoCloseable {

    private final DataSource dataSource;
    private final Runnable onRelease;
    private final AtomicBoolean released = new AtomicBoolean(false);

    public DatabaseLease(DataSource dataSource, Runnable onRelease) {
        this.dataSource = dataSource;
        this.onRelease = onRelease;
    }

    /**
     * A lease whose release is a no-op, for managers that do not pin pools (e.g. plain platform
     * database managers). The supplied data source is returned as-is.
     */
    public static DatabaseLease unmanaged(DataSource dataSource) {
        return new DatabaseLease(dataSource, () -> {
        });
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            onRelease.run();
        }
    }
}
