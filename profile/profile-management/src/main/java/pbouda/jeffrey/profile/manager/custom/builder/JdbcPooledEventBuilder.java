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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcPooledEventBuilder implements RecordBuilder<GenericRecord, List<JdbcPooledEventBuilder.Pool>> {

    public record Pool(String poolName, Map<Type, PoolEvent> events) {

        public Pool(String poolName) {
            this(poolName, new HashMap<>());
        }

        private PoolEvent findEvent(Type eventType) {
            return events.get(eventType);
        }

        private void addEvent(Type eventType, long elapsed) {
            events.put(eventType, new PoolEvent(elapsed));
        }
    }

    public static class PoolEvent {
        private long counter;
        private Duration accumulated;
        private long min;
        private long max;

        public PoolEvent(long elapsed) {
            this.counter = 1;
            this.accumulated = Duration.ofNanos(elapsed);
            this.min = elapsed;
            this.max = elapsed;
        }

        public long getCounter() {
            return counter;
        }

        public Duration getAccumulated() {
            return accumulated;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }
    }

    private final List<Pool> pools = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String poolName = fields.get("poolName").asText();
        Pool pool = findPool(poolName);

        if (pool == null) {
            pool = new Pool(poolName);
            pools.add(pool);
        }

        long elapsed = parseElapsed(fields);

        PoolEvent event = pool.findEvent(record.type());
        if (event == null) {
            pool.addEvent(record.type(), elapsed);
        } else {
            event.counter = event.counter + 1;
            event.accumulated = event.accumulated.plusNanos(elapsed);
            event.min = Math.min(event.min, elapsed);
            event.max = Math.max(event.max, elapsed);
        }
    }

    private static long parseElapsed(ObjectNode fields) {
        JsonNode elapsedJson = fields.get("elapsedTime");
        if (elapsedJson == null || elapsedJson.isNull()) {
            return 0;
        }
        return Long.parseLong(elapsedJson.asText());
    }

    private Pool findPool(String poolName) {
        for (Pool holder : pools) {
            if (holder.poolName.equals(poolName)) {
                return holder;
            }
        }
        return null;
    }

    @Override
    public List<Pool> build() {
        return pools;
    }
}
