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

package cafe.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, PoolStats> poolMap = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String poolName = fields.get("poolName").asString();

        int active = Integer.parseInt(fields.get("active").asString());
        int idle = Integer.parseInt(fields.get("idle").asString());
        int pendingThreads = Integer.parseInt(fields.get("pendingThreads").asString());

        PoolStats pool = poolMap.get(poolName);
        if (pool == null) {
            int maxConfigConnections = Integer.parseInt(fields.get("max").asString());
            int minConfigConnections = Integer.parseInt(fields.get("min").asString());

            PoolStats poolStats = PoolStats.create(
                    poolName,
                    active,
                    active + idle,
                    pendingThreads,
                    maxConfigConnections,
                    minConfigConnections);

            poolMap.put(poolName, poolStats);
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

    @Override
    public List<PoolStats> build() {
        return new ArrayList<>(poolMap.values());
    }
}
