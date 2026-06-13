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

import cafe.jeffrey.shared.common.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DatabaseManager} that keeps a separate connection pool open per database URI, so several
 * databases can be open and used concurrently. Each pool stays cached (warm) while it is in use and
 * is closed once it has been idle past a configurable timeout, releasing the embedded database
 * instance and its memory.
 * <p>
 * Unlike a single-slot manager, opening a different URI never closes another URI's pool — switching
 * between databases (or initializing two profiles at once) cannot tear down a pool that another
 * thread is still writing to. A long-running writer protects its pool from idle eviction by holding
 * a {@link DatabaseLease} via {@link #acquire(String)} for the duration of its work.
 * <p>
 * The returned {@link DataSource} is wrapped in an {@link UncloseableDataSource}: the pool's
 * lifecycle is owned exclusively by this manager, and defensive closes by callers are no-ops.
 */
public class CachingDatabaseManager implements DatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(CachingDatabaseManager.class);

    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(1);

    private static final class Slot {
        private final DataSource pool;
        private final UncloseableDataSource handle;
        private int pins;
        private Instant lastAccessed;

        private Slot(DataSource pool, Instant now) {
            this.pool = pool;
            this.handle = new UncloseableDataSource(pool);
            this.lastAccessed = now;
        }
    }

    private final DatabaseManager delegate;
    private final Clock clock;
    private final Duration idleTimeout;

    // Guarded by `this`. Opening, acquiring, releasing and evicting all hold the monitor, so a pool
    // can never be closed while it is being handed out to a caller.
    private final Map<String, Slot> slots = new HashMap<>();

    public CachingDatabaseManager(DatabaseManager delegate, Clock clock) {
        this(delegate, clock, DEFAULT_IDLE_TIMEOUT, DEFAULT_CHECK_INTERVAL, Schedulers.sharedSingleScheduled());
    }

    public CachingDatabaseManager(
            DatabaseManager delegate,
            Clock clock,
            Duration idleTimeout,
            Duration checkInterval,
            ScheduledExecutorService scheduler) {

        this.delegate = delegate;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        scheduler.scheduleAtFixedRate(
                this::evictExpired, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized DataSource open(String databaseUri) {
        return slot(databaseUri).handle;
    }

    @Override
    public synchronized DatabaseLease acquire(String databaseUri) {
        Slot slot = slot(databaseUri);
        slot.pins++;
        return new DatabaseLease(slot.handle, () -> release(databaseUri));
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        delegate.runMigrations(dataSource);
    }

    private Slot slot(String databaseUri) {
        Slot slot = slots.computeIfAbsent(databaseUri, uri -> new Slot(delegate.open(uri), clock.instant()));
        slot.lastAccessed = clock.instant();
        return slot;
    }

    private synchronized void release(String databaseUri) {
        Slot slot = slots.get(databaseUri);
        if (slot != null && slot.pins > 0) {
            slot.pins--;
            slot.lastAccessed = clock.instant();
        }
    }

    synchronized void evictExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Slot>> iterator = slots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Slot> entry = iterator.next();
            Slot slot = entry.getValue();
            boolean idle = slot.lastAccessed.plus(idleTimeout).isBefore(now);
            if (slot.pins == 0 && idle) {
                iterator.remove();
                LOG.info("Closing idle database pool: uri={} idle_for_ms={}",
                        entry.getKey(), Duration.between(slot.lastAccessed, now).toMillis());
                DataSourceUtils.close(slot.pool);
            }
        }
    }
}
