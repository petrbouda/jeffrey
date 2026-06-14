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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.io.FileForceStats.FileForceOp;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

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
