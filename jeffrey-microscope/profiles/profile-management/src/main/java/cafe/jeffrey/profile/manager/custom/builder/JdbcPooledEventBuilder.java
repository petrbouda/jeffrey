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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;

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
        private long accumulatedNanos;
        private long min;
        private long max;

        public PoolEvent(long elapsed) {
            this.counter = 1;
            this.accumulatedNanos = elapsed;
            this.min = elapsed;
            this.max = elapsed;
        }

        public long getCounter() {
            return counter;
        }

        public Duration getAccumulated() {
            return Duration.ofNanos(accumulatedNanos);
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }
    }

    private final Map<String, Pool> poolMap = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String poolName = fields.get("poolName").asString();
        Pool pool = poolMap.computeIfAbsent(poolName, Pool::new);

        long elapsed = parseElapsed(fields);

        PoolEvent event = pool.findEvent(record.type());
        if (event == null) {
            pool.addEvent(record.type(), elapsed);
        } else {
            event.counter = event.counter + 1;
            event.accumulatedNanos += elapsed;
            event.min = Math.min(event.min, elapsed);
            event.max = Math.max(event.max, elapsed);
        }
    }

    private static long parseElapsed(ObjectNode fields) {
        JsonNode elapsedJson = fields.get("elapsedTime");
        if (elapsedJson == null || elapsedJson.isNull()) {
            return 0;
        }
        return Long.parseLong(elapsedJson.asString());
    }

    @Override
    public List<Pool> build() {
        return new ArrayList<>(poolMap.values());
    }
}
