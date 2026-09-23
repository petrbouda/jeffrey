/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
