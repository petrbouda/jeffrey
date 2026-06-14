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
import cafe.jeffrey.profile.manager.model.io.FileForceStats.FileForceOp;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Aggregates {@code jdk.FileForce} (fsync) events into count + latency stats and keeps the slowest forces
 * in a bounded min-heap, returned longest-first.
 */
public class FileForceBuilder implements RecordBuilder<GenericRecord, FileForceStats> {

    private static final String PATH_FIELD = "path";
    private static final String METADATA_FIELD = "metaData";
    private static final String EVENT_THREAD_FIELD = "eventThread";

    private final int maxEntries;
    private final PriorityQueue<FileForceOp> slowest =
            new PriorityQueue<>(Comparator.comparingLong(FileForceOp::durationNanos));

    private long count;
    private long totalNanos;
    private long maxNanos;
    private long metadataCount;

    public FileForceBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();
        boolean metaData = Json.readBoolean(fields, METADATA_FIELD);

        count++;
        totalNanos += durationNanos;
        maxNanos = Math.max(maxNanos, durationNanos);
        if (metaData) {
            metadataCount++;
        }

        FileForceOp op = new FileForceOp(
                record.timestampFromStart().toMillis(),
                Json.readString(fields, PATH_FIELD),
                metaData,
                durationNanos,
                Json.readString(fields, EVENT_THREAD_FIELD));
        slowest.offer(op);
        if (slowest.size() > maxEntries) {
            slowest.poll();
        }
    }

    @Override
    public FileForceStats build() {
        List<FileForceOp> ordered = new ArrayList<>(slowest);
        ordered.sort(Comparator.comparingLong(FileForceOp::durationNanos).reversed());
        long avgNanos = count > 0 ? totalNanos / count : 0;
        return new FileForceStats(count, totalNanos, avgNanos, maxNanos, metadataCount, ordered);
    }
}
