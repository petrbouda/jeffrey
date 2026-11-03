/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.persistence.metrics;

import cafe.jeffrey.jfr.events.jdbc.pool.JdbcPoolStatisticsEvent;
import com.zaxxer.hikari.metrics.PoolStats;
import jdk.jfr.FlightRecorder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class JfrPoolStatisticsPeriodicRecorder implements Runnable {

    public static final JfrPoolStatisticsPeriodicRecorder INSTANCE = new JfrPoolStatisticsPeriodicRecorder();

    /** The Flight Recorder takes one hook once per JVM, and refuses it a second time. */
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private final Map<String, PoolStats> pools = new HashMap<>();

    private JfrPoolStatisticsPeriodicRecorder() {
    }

    /**
     * Registers the one hook with the Flight Recorder, once per JVM: a second application context
     * in the same JVM (a test booting the application with another configuration) finds it there.
     */
    public static void registerToFlightRecorder() {
        if (REGISTERED.compareAndSet(false, true)) {
            FlightRecorder.addPeriodicEvent(JdbcPoolStatisticsEvent.class, INSTANCE);
        }
    }

    public void run() {
        for (Map.Entry<String, PoolStats> entry : pools.entrySet()) {
            PoolStats poolStats = entry.getValue();

            JdbcPoolStatisticsEvent event = new JdbcPoolStatisticsEvent();
            event.poolName = entry.getKey();
            event.active = poolStats.getActiveConnections();
            event.idle = poolStats.getIdleConnections();
            event.total = poolStats.getTotalConnections();
            event.max = poolStats.getMaxConnections();
            event.min = poolStats.getMinConnections();
            event.pendingThreads = poolStats.getPendingThreads();
            event.commit();
        }
    }

    public static void addPool(String poolName, PoolStats poolStats) {
        INSTANCE.pools.put(poolName, poolStats);
    }

    public static void removePool(String poolName) {
        INSTANCE.pools.remove(poolName);
    }
}
