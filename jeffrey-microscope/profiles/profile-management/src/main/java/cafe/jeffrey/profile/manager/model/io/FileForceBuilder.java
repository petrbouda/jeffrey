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
