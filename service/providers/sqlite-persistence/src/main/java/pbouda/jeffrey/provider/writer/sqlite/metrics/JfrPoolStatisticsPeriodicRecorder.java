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

import com.zaxxer.hikari.metrics.PoolStats;
import jdk.jfr.FlightRecorder;
import pbouda.jeffrey.jfr.types.jdbc.pool.PoolStatisticsEvent;

import java.util.HashMap;
import java.util.Map;

public class JfrPoolStatisticsPeriodicRecorder implements Runnable {

    public static final JfrPoolStatisticsPeriodicRecorder INSTANCE = new JfrPoolStatisticsPeriodicRecorder();

    private final Map<String, PoolStats> pools = new HashMap<>();

    private JfrPoolStatisticsPeriodicRecorder() {
    }

    public static void registerToFlightRecorder() {
        FlightRecorder.addPeriodicEvent(PoolStatisticsEvent.class, INSTANCE);
    }

    public void run() {
        for (Map.Entry<String, PoolStats> entry : pools.entrySet()) {
            PoolStats poolStats = entry.getValue();

            PoolStatisticsEvent event = new PoolStatisticsEvent();
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
