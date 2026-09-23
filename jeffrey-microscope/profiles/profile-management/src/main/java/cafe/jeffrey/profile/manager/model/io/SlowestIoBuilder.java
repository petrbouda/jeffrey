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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the slowest I/O operations across socket/file read/write events in a bounded min-heap,
 * returned longest-first.
 */
public class SlowestIoBuilder implements RecordBuilder<GenericRecord, List<IoOperation>> {

    private final int maxEntries;
    private final PriorityQueue<IoOperation> slowest =
            new PriorityQueue<>(Comparator.comparingLong(IoOperation::durationNanos));

    public SlowestIoBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        ObjectNode fields = record.jsonFields();
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        IoOperation operation = new IoOperation(
                IoEventFields.kindLabel(type),
                IoEventFields.target(type, fields),
                IoEventFields.bytes(type, fields),
                durationNanos,
                Json.readString(fields, IoEventFields.EVENT_THREAD_FIELD));

        slowest.offer(operation);
        if (slowest.size() > maxEntries) {
            slowest.poll();
        }
    }

    @Override
    public List<IoOperation> build() {
        List<IoOperation> ordered = new ArrayList<>(slowest);
        ordered.sort(Comparator.comparingLong(IoOperation::durationNanos).reversed());
        return ordered;
    }
}
