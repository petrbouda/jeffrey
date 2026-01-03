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

package pbouda.jeffrey.profile.manager.custom.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.provider.profile.model.GenericRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class JdbcPoolStatisticsBuilder implements
        RecordBuilder<GenericRecord, List<JdbcPoolStatisticsBuilder.PoolStats>> {

    public record PoolStats(
            String poolName,
            AtomicLong counter,
            AtomicInteger maxActive,
            AtomicLong cumulatedActive,
            AtomicInteger maxConnections,
            AtomicInteger maxPendingThreads,
            int maxConfigConnections,
            int minConfigConnections,
            AtomicLong pendingThreadsPeriods) {

        private static PoolStats create(
                String poolName,
                int activeConnections,
                int maxConnections,
                int maxPendingThreads,
                int maxConfigConnections,
                int minConfigConnections
        ) {
            return new PoolStats(
                    poolName,
                    new AtomicLong(1),
                    new AtomicInteger(activeConnections),
                    new AtomicLong(activeConnections),
                    new AtomicInteger(maxConnections),
                    new AtomicInteger(maxPendingThreads),
                    maxConfigConnections,
                    minConfigConnections,
                    new AtomicLong(maxPendingThreads > 0 ? 1 : 0));
        }
    }

    private final List<PoolStats> pools = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String poolName = fields.get("poolName").asText();
        PoolStats pool = findPool(poolName);

        int active = Integer.parseInt(fields.get("active").asText());
        int idle = Integer.parseInt(fields.get("idle").asText());
        int pendingThreads = Integer.parseInt(fields.get("pendingThreads").asText());

        if (pool == null) {
            int maxConfigConnections = Integer.parseInt(fields.get("max").asText());
            int minConfigConnections = Integer.parseInt(fields.get("min").asText());

            PoolStats poolStats = PoolStats.create(
                    poolName,
                    active,
                    active + idle,
                    pendingThreads,
                    maxConfigConnections,
                    minConfigConnections);

            pools.add(poolStats);
        } else {
            pool.counter.incrementAndGet();
            pool.maxActive.set(Math.max(pool.maxActive.get(), active));
            pool.cumulatedActive.addAndGet(active);
            pool.maxConnections.set(Math.max(pool.maxConnections.get(), active + idle));
            pool.maxPendingThreads.set(Math.max(pool.maxPendingThreads.get(), pendingThreads));
            if (pendingThreads > 0) {
                pool.pendingThreadsPeriods.incrementAndGet();
            }
        }
    }

    private PoolStats findPool(String poolName) {
        for (PoolStats holder : pools) {
            if (holder.poolName.equals(poolName)) {
                return holder;
            }
        }
        return null;
    }

    @Override
    public List<PoolStats> build() {
        return pools;
    }
}
