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

package cafe.jeffrey.jfr.events.jdbc.hikari;

import cafe.jeffrey.jfr.events.jdbc.pool.AcquiringPooledJdbcConnectionTimeoutEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.JdbcPoolStatisticsEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionAcquiredEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionBorrowedEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionCreatedEvent;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import jdk.jfr.FlightRecorder;

import java.util.concurrent.TimeUnit;

/**
 * Emits Jeffrey's connection-pool events from HikariCP.
 * <p>
 * These event types have shipped since the beginning with nothing to produce them, so the pool
 * dashboard has had no source. This is that source: Hikari already measures everything the events
 * describe and hands it to a {@link MetricsTrackerFactory}, so the instrumentation is a mapping
 * rather than a measurement.
 *
 * <pre>{@code
 * HikariConfig config = new HikariConfig();
 * config.setMetricsTrackerFactory(new JfrMetricsTrackerFactory());
 * }</pre>
 *
 * Two kinds of event come out of it:
 * <ul>
 *   <li><b>Per operation</b> — how long an acquire, a borrow or a physical connection creation
 *       took, and every acquire that timed out.</li>
 *   <li><b>Periodic</b> — a {@link JdbcPoolStatisticsEvent} sampled by JFR itself on the period the
 *       event declares, reading the live gauges Hikari exposes. Registered once per pool when the
 *       pool asks for a tracker.</li>
 * </ul>
 * Pool events are deliberately not spans: the pool reports after the fact, with no interval of its
 * own for JFR to time. Whatever waited for the connection is the span; these hang off it.
 */
public class JfrMetricsTrackerFactory implements MetricsTrackerFactory {

    @Override
    public IMetricsTracker create(String poolName, PoolStats poolStats) {
        PoolStatisticsSampler sampler = new PoolStatisticsSampler(poolName, poolStats);
        FlightRecorder.addPeriodicEvent(JdbcPoolStatisticsEvent.class, sampler);
        return new JfrMetricsTracker(poolName, sampler);
    }

    /**
     * Fills the periodic gauge event from Hikari's live pool statistics.
     * <p>
     * Reading the gauges only when JFR asks is the point: the sampler is registered once per pool
     * and costs nothing between recordings, because JFR does not call it when the event type is
     * disabled.
     */
    private record PoolStatisticsSampler(String poolName, PoolStats poolStats) implements Runnable {

        @Override
        public void run() {
            JdbcPoolStatisticsEvent event = new JdbcPoolStatisticsEvent();
            if (!event.shouldCommit()) {
                return;
            }
            event.poolName = poolName;
            event.total = poolStats.getTotalConnections();
            event.idle = poolStats.getIdleConnections();
            event.active = poolStats.getActiveConnections();
            event.max = poolStats.getMaxConnections();
            event.min = poolStats.getMinConnections();
            event.pendingThreads = poolStats.getPendingThreads();
            event.commit();
        }
    }

    /**
     * Maps Hikari's per-operation callbacks onto the pool events.
     * <p>
     * Hikari reports two of the three durations in milliseconds while the events record a timespan
     * in nanoseconds, so those are converted rather than recorded in the wrong unit — a pool that
     * looked a million times faster than it is would be worse than no instrumentation.
     */
    private record JfrMetricsTracker(String poolName, PoolStatisticsSampler sampler) implements IMetricsTracker {

        @Override
        public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
            PooledJdbcConnectionAcquiredEvent event = new PooledJdbcConnectionAcquiredEvent();
            if (event.shouldCommit()) {
                event.poolName = poolName;
                event.elapsedTime = elapsedAcquiredNanos;
                event.commit();
            }
        }

        @Override
        public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
            PooledJdbcConnectionBorrowedEvent event = new PooledJdbcConnectionBorrowedEvent();
            if (event.shouldCommit()) {
                event.poolName = poolName;
                event.elapsedTime = TimeUnit.MILLISECONDS.toNanos(elapsedBorrowedMillis);
                event.commit();
            }
        }

        @Override
        public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
            PooledJdbcConnectionCreatedEvent event = new PooledJdbcConnectionCreatedEvent();
            if (event.shouldCommit()) {
                event.poolName = poolName;
                event.elapsedTime = TimeUnit.MILLISECONDS.toNanos(connectionCreatedMillis);
                event.commit();
            }
        }

        @Override
        public void recordConnectionTimeout() {
            AcquiringPooledJdbcConnectionTimeoutEvent event = new AcquiringPooledJdbcConnectionTimeoutEvent();
            if (event.shouldCommit()) {
                event.poolName = poolName;
                event.commit();
            }
        }

        /**
         * Stops the periodic sampler when the pool shuts down, so a closed pool's gauges are not
         * read for the lifetime of the JVM.
         */
        @Override
        public void close() {
            FlightRecorder.removePeriodicEvent(sampler);
        }
    }
}
