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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * A {@link DatabaseManager} that keeps at most one database open at a time.
 * <p>
 * Opening the same URI again returns the already-open {@link DataSource}. Opening a different URI
 * eagerly closes the previous database's connection pool before the new one is created, so the
 * previous embedded database instance releases its memory (e.g. the DuckDB buffer pool) and file
 * handles deterministically at the switch, instead of lingering until an idle-based eviction.
 * <p>
 * The returned {@link DataSource} is wrapped in an {@link UncloseableDataSource}: the pool's
 * lifecycle is owned exclusively by this manager, and defensive closes by callers are no-ops.
 * <p>
 * Intended for the per-profile databases in jeffrey-microscope, where only a single profile is
 * opened at a time. Database managers serving multiple concurrently-open databases must not use
 * this wrapper.
 */
public class SingleSlotDatabaseManager implements DatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(SingleSlotDatabaseManager.class);

    private record Slot(String databaseUri, DataSource pool, UncloseableDataSource handle) {
    }

    private final DatabaseManager delegate;

    private Slot current;

    public SingleSlotDatabaseManager(DatabaseManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized DataSource open(String databaseUri) {
        if (current != null) {
            if (current.databaseUri().equals(databaseUri)) {
                return current.handle();
            }

            LOG.info("Closing previously opened database before switching: previous_uri={} next_uri={}",
                    current.databaseUri(), databaseUri);
            DataSourceUtils.close(current.pool());
            current = null;
        }

        DataSource pool = delegate.open(databaseUri);
        current = new Slot(databaseUri, pool, new UncloseableDataSource(pool));
        return current.handle();
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        delegate.runMigrations(dataSource);
    }
}
