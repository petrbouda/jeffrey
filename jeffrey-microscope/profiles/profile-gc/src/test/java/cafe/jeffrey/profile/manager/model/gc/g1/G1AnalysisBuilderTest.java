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

package cafe.jeffrey.profile.manager.model.gc.g1;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.PausePhase;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.RegionSnapshot;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("G1AnalysisBuilder")
class G1AnalysisBuilderTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord rec(Type type, long secondsFromStart, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ofNanos(durationNanos),
                null, null, 0L, 0L, fields);
    }

    private static G1AnalysisBuilder newBuilder() {
        return new G1AnalysisBuilder(new RelativeTimeRange(0, 10_000));
    }

    private static ObjectNode gc(long gcId, String name, long sumOfPauses) {
        ObjectNode node = Json.createObject();
        node.put("gcId", gcId);
        node.put("name", name);
        node.put("sumOfPauses", sumOfPauses);
        return node;
    }

    private static ObjectNode g1gc(long gcId, String type) {
        ObjectNode node = Json.createObject();
        node.put("gcId", gcId);
        node.put("type", type);
        return node;
    }

    private static ObjectNode phase(String name) {
        ObjectNode node = Json.createObject();
        node.put("name", name);
        return node;
    }

    @Test
    @DisplayName("Classifies collections into young, mixed and full with pause statistics")
    void classifiesCollections() {
        G1AnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.GARBAGE_COLLECTION, 1, 0, gc(0, "G1New", 1_000_000)));
        builder.onRecord(rec(Type.G1_GARBAGE_COLLECTION, 1, 0, g1gc(0, "Normal")));
        builder.onRecord(rec(Type.GARBAGE_COLLECTION, 2, 0, gc(1, "G1New", 3_000_000)));
        builder.onRecord(rec(Type.G1_GARBAGE_COLLECTION, 2, 0, g1gc(1, "Mixed")));
        builder.onRecord(rec(Type.GARBAGE_COLLECTION, 3, 0, gc(2, "G1Full", 9_000_000)));

        var header = builder.build().header();

        assertEquals(1, header.youngCount());
        assertEquals(1, header.mixedCount());
        assertEquals(1, header.fullCount());
        assertEquals(13_000_000, header.totalPauseNanos());
        assertEquals(9_000_000, header.maxPauseNanos());
    }

    @Test
    @DisplayName("Aggregates pause sub-phases by name, sorted by total time descending")
    void aggregatesPausePhases() {
        G1AnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.GC_PHASE_PAUSE, 1, 5_000_000, phase("Pause")));
        builder.onRecord(rec(Type.GC_PHASE_PAUSE_LEVEL_1, 1, 2_000_000, phase("Object Copy")));
        builder.onRecord(rec(Type.GC_PHASE_PAUSE_LEVEL_1, 1, 2_000_000, phase("Object Copy")));

        List<PausePhase> phases = builder.build().pausePhases();

        assertEquals("Pause", phases.getFirst().name());
        PausePhase objectCopy = phases.stream()
                .filter(p -> p.name().equals("Object Copy"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, objectCopy.count());
        assertEquals(4_000_000, objectCopy.totalNanos());
        assertEquals(1, objectCopy.level());
    }

    @Test
    @DisplayName("Counts evacuation failures per gcId")
    void countsEvacuationFailures() {
        G1AnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.EVACUATION_FAILED, 1, 0, gcIdOnly(2)));
        builder.onRecord(rec(Type.EVACUATION_FAILED, 1, 0, gcIdOnly(2)));
        builder.onRecord(rec(Type.EVACUATION_FAILED, 2, 0, gcIdOnly(5)));

        G1AnalysisData data = builder.build();

        assertEquals(3, data.header().evacuationFailureCount());
        assertEquals(2, data.evacuationFailures().size());
        assertEquals(5, data.evacuationFailures().getFirst().gcId());
        assertEquals(1, data.evacuationFailures().getFirst().count());
    }

    @Test
    @DisplayName("Groups region-information events into a per-timestamp snapshot")
    void groupsRegionSnapshots() {
        G1AnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.G1_HEAP_REGION_INFORMATION, 1, 0, region(1, "Old", 20)));
        builder.onRecord(rec(Type.G1_HEAP_REGION_INFORMATION, 1, 0, region(0, "Eden", 10)));

        List<RegionSnapshot> snapshots = builder.build().regionSnapshots();

        assertEquals(1, snapshots.size());
        assertEquals(2, snapshots.getFirst().regions().size());
        assertEquals(0, snapshots.getFirst().regions().getFirst().index());
    }

    @Test
    @DisplayName("Builds Eden/Survivor/Old composition from the After-GC heap summary")
    void buildsComposition() {
        G1AnalysisBuilder builder = newBuilder();
        ObjectNode summary = Json.createObject();
        summary.put("when", "After GC");
        summary.put("edenUsedSize", 100L);
        summary.put("survivorUsedSize", 50L);
        summary.put("oldGenUsedSize", 200L);
        summary.put("numberOfRegions", 10);
        builder.onRecord(rec(Type.G1_HEAP_SUMMARY, 2, 0, summary));

        G1AnalysisData data = builder.build();

        assertEquals(10, data.header().regionCount());
        assertEquals("Eden", data.regionComposition().series().getFirst().name());
        long maxEden = data.regionComposition().series().getFirst().data().stream()
                .mapToLong(point -> point.get(1))
                .max()
                .orElse(0);
        assertTrue(maxEden >= 100, "Eden series should carry the After-GC used size");
    }

    private static ObjectNode gcIdOnly(long gcId) {
        ObjectNode node = Json.createObject();
        node.put("gcId", gcId);
        return node;
    }

    private static ObjectNode region(int index, String type, long used) {
        ObjectNode node = Json.createObject();
        node.put("index", index);
        node.put("type", type);
        node.put("used", used);
        return node;
    }
}
