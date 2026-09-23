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

package cafe.jeffrey.profile.manager.model.classloading;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Aggregates {@code jdk.ClassLoad} events into a total count plus the slowest individual loads.
 * The slowest loads are kept in a bounded min-heap so memory stays constant even when the recording
 * contains millions of class-load events.
 */
public class ClassLoadActivityBuilder implements RecordBuilder<GenericRecord, ClassLoadActivity> {

    private static final String LOADED_CLASS_FIELD = "loadedClass";
    private static final String DEFINING_CLASS_LOADER_FIELD = "definingClassLoader";

    private final int maxEntries;
    private final PriorityQueue<ClassLoadEntry> slowest =
            new PriorityQueue<>(Comparator.comparingLong(ClassLoadEntry::durationNanos));

    private long totalCount;

    public ClassLoadActivityBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        totalCount++;

        ObjectNode fields = record.jsonFields();
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        ClassLoadEntry entry = new ClassLoadEntry(
                Json.readString(fields, LOADED_CLASS_FIELD),
                durationNanos,
                Json.readString(fields, DEFINING_CLASS_LOADER_FIELD));

        slowest.offer(entry);
        if (slowest.size() > maxEntries) {
            slowest.poll();
        }
    }

    @Override
    public ClassLoadActivity build() {
        List<ClassLoadEntry> ordered = new ArrayList<>(slowest);
        ordered.sort(Comparator.comparingLong(ClassLoadEntry::durationNanos).reversed());
        return new ClassLoadActivity(totalCount, ordered);
    }
}
