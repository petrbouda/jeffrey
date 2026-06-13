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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

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
