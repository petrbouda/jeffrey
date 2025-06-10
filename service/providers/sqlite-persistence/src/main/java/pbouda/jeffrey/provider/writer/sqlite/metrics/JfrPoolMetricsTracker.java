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

package pbouda.jeffrey.provider.writer.sqlite.metrics;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import pbouda.jeffrey.jfr.types.jdbc.pool.AcquiringPooledJdbcConnectionTimeoutEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledJdbcConnectionAcquiredEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledJdbcConnectionBorrowedEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledJdbcConnectionCreatedEvent;

public class JfrPoolMetricsTracker implements IMetricsTracker {

    private final String poolName;

    public JfrPoolMetricsTracker(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void recordConnectionCreatedMillis(long createdMs) {
        PooledJdbcConnectionCreatedEvent event = new PooledJdbcConnectionCreatedEvent();
        event.poolName = this.poolName;
        event.creationTime = createdMs;
        event.commit();
    }

    @Override
    public void recordConnectionAcquiredNanos(long acquiredNs) {
        PooledJdbcConnectionAcquiredEvent event = new PooledJdbcConnectionAcquiredEvent();
        event.poolName = this.poolName;
        event.acquireTime = acquiredNs;
        event.commit();
    }

    @Override
    public void recordConnectionUsageMillis(long borrowedMs) {
        PooledJdbcConnectionBorrowedEvent event = new PooledJdbcConnectionBorrowedEvent();
        event.poolName = this.poolName;
        event.borrowTime = borrowedMs;
        event.commit();
    }

    @Override
    public void recordConnectionTimeout() {
        AcquiringPooledJdbcConnectionTimeoutEvent event = new AcquiringPooledJdbcConnectionTimeoutEvent();
        event.poolName = this.poolName;
        event.commit();
    }
}
