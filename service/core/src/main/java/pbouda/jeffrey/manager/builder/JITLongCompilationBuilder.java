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

package pbouda.jeffrey.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.common.event.JITLongCompilation;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;

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
