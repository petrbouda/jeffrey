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
import pbouda.jeffrey.jfr.types.jdbc.pool.AcquiringPooledConnectionTimeoutEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledConnectionAcquiredEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledConnectionBorrowedEvent;
import pbouda.jeffrey.jfr.types.jdbc.pool.PooledConnectionCreatedEvent;

public class JfrPoolMetricsTracker implements IMetricsTracker {

    private final String poolName;

    public JfrPoolMetricsTracker(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void recordConnectionCreatedMillis(long createdMs) {
        PooledConnectionCreatedEvent event = new PooledConnectionCreatedEvent();
        event.poolName = this.poolName;
        event.creationTime = createdMs;
        event.commit();
    }

    @Override
    public void recordConnectionAcquiredNanos(long acquiredNs) {
        PooledConnectionAcquiredEvent event = new PooledConnectionAcquiredEvent();
        event.poolName = this.poolName;
        event.acquireTime = acquiredNs;
        event.commit();
    }

    @Override
    public void recordConnectionUsageMillis(long borrowedMs) {
        PooledConnectionBorrowedEvent event = new PooledConnectionBorrowedEvent();
        event.poolName = this.poolName;
        event.borrowTime = borrowedMs;
        event.commit();
    }

    @Override
    public void recordConnectionTimeout() {
        AcquiringPooledConnectionTimeoutEvent event = new AcquiringPooledConnectionTimeoutEvent();
        event.poolName = this.poolName;
        event.commit();
    }
}
