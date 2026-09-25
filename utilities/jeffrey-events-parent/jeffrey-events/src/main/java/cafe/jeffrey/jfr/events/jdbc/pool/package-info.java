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

/**
 * Connection-pool events feeding Jeffrey's pool dashboard: a periodic gauge snapshot plus the
 * pool's own duration and failure reports.
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.pool.JdbcPoolStatisticsEvent} ({@code
 *       jeffrey.JdbcPoolStatistics}) — total/idle/active/max/min connections and pending threads,
 *       sampled every second ({@code @Period("1 s")})</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionAcquiredEvent},
 *       {@link cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionBorrowedEvent},
 *       {@link cafe.jeffrey.jfr.events.jdbc.pool.PooledJdbcConnectionCreatedEvent} — how long an
 *       acquire, a borrow, or a physical connection creation took</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.pool.AcquiringPooledJdbcConnectionTimeoutEvent} — an
 *       acquire that gave up</li>
 * </ul>
 * These are plain events, not spans — see
 * {@link cafe.jeffrey.jfr.events.jdbc.pool.JdbcPoolEvent} for why. Emit them from the pool's own
 * hook points; HikariCP's {@code MetricsTrackerFactory} exposes exactly the callbacks these events
 * mirror:
 *
 * <pre>{@code
 * public class JfrMetricsTracker implements IMetricsTracker {
 *     private final String poolName;
 *
 *     public JfrMetricsTracker(String poolName) {
 *         this.poolName = poolName;
 *     }
 *
 *     @Override
 *     public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
 *         PooledJdbcConnectionAcquiredEvent event = new PooledJdbcConnectionAcquiredEvent();
 *         event.poolName = poolName;
 *         event.elapsedTime = elapsedAcquiredNanos;
 *         event.commit();
 *     }
 *
 *     @Override
 *     public void recordConnectionTimeout() {
 *         AcquiringPooledJdbcConnectionTimeoutEvent event =
 *                 new AcquiringPooledJdbcConnectionTimeoutEvent();
 *         event.poolName = poolName;
 *         event.commit();
 *     }
 * }
 * }</pre>
 *
 * The statistics event is periodic: register a hook once and fill it from the pool's gauges — JFR
 * calls it on its own schedule, so there is nothing to emit per operation:
 *
 * <pre>{@code
 * FlightRecorder.addPeriodicEvent(JdbcPoolStatisticsEvent.class, () -> {
 *     JdbcPoolStatisticsEvent event = new JdbcPoolStatisticsEvent();
 *     event.poolName = poolName;
 *     event.total = poolBean.getTotalConnections();
 *     event.active = poolBean.getActiveConnections();
 *     event.idle = poolBean.getIdleConnections();
 *     event.pendingThreads = poolBean.getThreadsAwaitingConnection();
 *     event.commit();
 * });
 * }</pre>
 */
package cafe.jeffrey.jfr.events.jdbc.pool;
