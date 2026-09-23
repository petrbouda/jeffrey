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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class JITLongCompilationBuilder implements RecordBuilder<GenericRecord, List<JITLongCompilation>> {

    private record TempHolder(long duration, ObjectNode jsonFields) {
    }

    private final int limit;

    private final PriorityQueue<TempHolder> topTempHolder;

    public JITLongCompilationBuilder(int limit) {
        this.limit = limit;

        // Min heaps to keep the highest values by removing the smallest when full
        this.topTempHolder = new PriorityQueue<>(Comparator.comparing(TempHolder::duration));
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long duration = fields.get("duration").asLong();

        TempHolder holder = new TempHolder(duration, fields);

        // Maintain top system CPU loads
        if (topTempHolder.size() < limit) {
            topTempHolder.add(holder);
        } else if (holder.duration > topTempHolder.peek().duration()) {
            topTempHolder.poll(); // Remove the smallest element
            topTempHolder.add(holder);
        }
    }

    @Override
    public List<JITLongCompilation> build() {
        // Convert priority queues to lists
        List<TempHolder> userList = new ArrayList<>(topTempHolder);

        // Sort lists in descending order of CPU load
        userList.sort(Comparator.comparing(TempHolder::duration).reversed());

        return userList.stream()
                .map(holder -> Json.treeToValue(holder.jsonFields(), JITLongCompilation.class))
                .toList();
    }
}
