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

package cafe.jeffrey.profile.manager.model.gc;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizerStatsBuilder;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringDeduplicationBuilder;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesBuilder;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GC table/finalizer builders")
class GcTableFinalizerBuildersTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord rec(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ZERO,
                null, null, 0L, 0L, fields);
    }

    @Nested
    @DisplayName("StringSymbolTablesBuilder")
    class Tables {

        @Test
        @DisplayName("Separates String and Symbol table entries/footprint and tracks peaks")
        void separatesTables() {
            StringSymbolTablesBuilder builder = new StringSymbolTablesBuilder(new RelativeTimeRange(0, 10_000));
            builder.onRecord(rec(Type.STRING_TABLE_STATISTICS, 1, tableFields(1000, 50_000)));
            builder.onRecord(rec(Type.STRING_TABLE_STATISTICS, 2, tableFields(1500, 70_000)));
            builder.onRecord(rec(Type.SYMBOL_TABLE_STATISTICS, 1, tableFields(8000, 400_000)));

            StringSymbolTablesData data = builder.build();

            assertEquals(1500, data.header().peakStringEntries());
            assertEquals(70_000, data.header().peakStringFootprint());
            assertEquals(8000, data.header().peakSymbolEntries());
            assertEquals(400_000, data.header().peakSymbolFootprint());
            assertEquals("String Table", data.entries().series().getFirst().name());
            assertEquals("Symbol Table", data.entries().series().get(1).name());

            long maxStringEntries = data.entries().series().getFirst().data().stream()
                    .mapToLong(point -> point.get(1))
                    .max()
                    .orElse(0);
            assertEquals(1500, maxStringEntries);
        }

        private ObjectNode tableFields(long entryCount, long totalFootprint) {
            ObjectNode node = Json.createObject();
            node.put("entryCount", entryCount);
            node.put("totalFootprint", totalFootprint);
            return node;
        }
    }

    @Nested
    @DisplayName("StringDeduplicationBuilder")
    class Deduplication {

        @Test
        @DisplayName("Sums cycle totals and builds the activity timeline")
        void sumsCycles() {
            StringDeduplicationBuilder builder = new StringDeduplicationBuilder(new RelativeTimeRange(0, 10_000));
            builder.onRecord(rec(Type.STRING_DEDUPLICATION, 1, dedupFields(1000, 200, 800, 4096)));
            builder.onRecord(rec(Type.STRING_DEDUPLICATION, 2, dedupFields(500, 100, 400, 2048)));

            StringSymbolTablesData.Deduplication data = builder.build();

            assertEquals(2, data.cycles());
            assertEquals(1500, data.totalInspected());
            assertEquals(300, data.totalDeduplicated());
            assertEquals(1200, data.totalNewStrings());
            assertEquals(6144, data.totalBytesSaved());
            assertEquals("Deduplicated", data.timeline().series().getFirst().name());
            assertEquals("Bytes Saved", data.timeline().series().get(1).name());
        }

        private ObjectNode dedupFields(long inspected, long deduplicated, long newStrings, long deduplicatedSize) {
            ObjectNode node = Json.createObject();
            node.put("inspected", inspected);
            node.put("deduplicated", deduplicated);
            node.put("newStrings", newStrings);
            node.put("deduplicatedSize", deduplicatedSize);
            return node;
        }
    }

    @Nested
    @DisplayName("FinalizerStatsBuilder")
    class Finalizers {

        @Test
        @DisplayName("Groups by class keeping peak objects and total finalizers run")
        void groupsByClass() {
            FinalizerStatsBuilder builder = new FinalizerStatsBuilder(10);
            builder.onRecord(rec(Type.FINALIZER_STATISTICS, 1, finalizerFields("com.A", 10, 2)));
            builder.onRecord(rec(Type.FINALIZER_STATISTICS, 2, finalizerFields("com.A", 25, 5)));
            builder.onRecord(rec(Type.FINALIZER_STATISTICS, 1, finalizerFields("com.B", 3, 100)));

            FinalizersData data = builder.build();

            assertEquals(2, data.header().classCount());
            assertEquals(28, data.header().totalPendingObjects());
            assertEquals(105, data.header().totalFinalizersRun());

            // Ranked by peak pending objects: com.A (25) before com.B (3).
            assertEquals("com.A", data.classes().getFirst().className());
            assertEquals(25, data.classes().getFirst().peakObjects());
            assertEquals(5, data.classes().getFirst().finalizersRun());
            assertEquals("com.B", data.classes().get(1).className());
        }

        private ObjectNode finalizerFields(String className, long objects, long totalRun) {
            ObjectNode node = Json.createObject();
            node.put("finalizableClass", className);
            node.put("codeSource", "file:/app.jar");
            node.put("objects", objects);
            node.put("totalFinalizersRun", totalRun);
            return node;
        }
    }
}
