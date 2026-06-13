/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
