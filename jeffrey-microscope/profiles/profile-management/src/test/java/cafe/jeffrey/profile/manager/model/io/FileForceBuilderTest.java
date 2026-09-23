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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.io.FileForceStats.FileForceOp;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FileForceBuilder")
class FileForceBuilderTest {

    private static GenericRecord forceEvent(String path, boolean metaData, long durationNanos) {
        ObjectNode fields = Json.createObject();
        fields.put("path", path);
        fields.put("metaData", metaData);
        fields.put("eventThread", "main");
        return new GenericRecord(
                Type.FILE_FORCE, "File Force", Instant.EPOCH, Duration.ofMillis(10),
                Duration.ofNanos(durationNanos), null, null, 0, 0, fields);
    }

    @Test
    @DisplayName("Aggregates count, latency stats and metadata flushes, slowest first")
    void aggregates() {
        FileForceBuilder builder = new FileForceBuilder(10);
        builder.onRecord(forceEvent("/a.log", false, 100));
        builder.onRecord(forceEvent("/b.log", true, 300));
        builder.onRecord(forceEvent("/c.log", false, 200));

        FileForceStats stats = builder.build();

        assertTrue(stats.hasEvents());
        assertEquals(3, stats.count());
        assertEquals(600, stats.totalNanos());
        assertEquals(200, stats.avgNanos());
        assertEquals(300, stats.maxNanos());
        assertEquals(1, stats.metadataCount());

        assertEquals(3, stats.slowest().size());
        FileForceOp slowest = stats.slowest().getFirst();
        assertEquals("/b.log", slowest.path());
        assertTrue(slowest.metaData());
        assertEquals(300, slowest.durationNanos());
    }

    @Test
    @DisplayName("Empty stream produces a zeroed, event-free summary")
    void empty() {
        FileForceStats stats = new FileForceBuilder(10).build();
        assertEquals(0, stats.count());
        assertEquals(0, stats.avgNanos());
        org.junit.jupiter.api.Assertions.assertFalse(stats.hasEvents());
    }
}
