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

package pbouda.jeffrey.provider.writer.sqlite;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;
import pbouda.jeffrey.jfr.types.hikaricp.*;

public class JfrMetricsTracker implements IMetricsTracker {

  private final String poolName;
  private final PoolStats poolStats;

  JfrMetricsTracker(String poolName, PoolStats poolStats) {
    this.poolName = poolName;
    this.poolStats = poolStats;
  }

  private void commitPoolStatistics() {
    PoolStatisticsEvent event = new PoolStatisticsEvent();
    event.poolName = poolName;
    event.active = poolStats.getActiveConnections();
    event.idle = poolStats.getIdleConnections();
    event.total = poolStats.getTotalConnections();
    event.max = poolStats.getMaxConnections();
    event.min = poolStats.getMinConnections();
    event.pendingThreads = poolStats.getPendingThreads();
    event.commit();
  }

  @Override
  public void recordConnectionCreatedMillis(long createdMs) {
    PooledConnectionCreatedEvent event = new PooledConnectionCreatedEvent();
    event.poolName = this.poolName;
    event.creationTime = createdMs;
    event.commit();
    commitPoolStatistics();
  }

  @Override
  public void recordConnectionAcquiredNanos(long acquiredNs) {
    PooledConnectionAcquiredEvent event = new PooledConnectionAcquiredEvent();
    event.poolName = this.poolName;
    event.acquireTime = acquiredNs;
    event.commit();
    commitPoolStatistics();
  }

  @Override
  public void recordConnectionUsageMillis(long borrowedMs) {
    PooledConnectionBorrowedEvent event = new PooledConnectionBorrowedEvent();
    event.poolName = this.poolName;
    event.borrowTime = borrowedMs;
    event.commit();
    commitPoolStatistics();
  }

  @Override
  public void recordConnectionTimeout() {
    AcquiringPooledConnectionTimeoutEvent event = new AcquiringPooledConnectionTimeoutEvent();
    event.poolName = this.poolName;
    event.commit();
    commitPoolStatistics();
  }
}
