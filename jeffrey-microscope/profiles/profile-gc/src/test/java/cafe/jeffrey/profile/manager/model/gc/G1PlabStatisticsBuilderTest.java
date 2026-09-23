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

package cafe.jeffrey.profile.manager.model.gc;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("G1PlabStatisticsBuilder")
class G1PlabStatisticsBuilderTest {

    private static GenericRecord evacuation(
            Type type, long gcId, long allocated, long used,
            long wasted, long undoWaste, long regionEndWaste, long failureWaste) {
        ObjectNode fields = Json.createObject();
        fields.put("statistics.gcId", gcId);
        fields.put("statistics.allocated", allocated);
        fields.put("statistics.used", used);
        fields.put("statistics.wasted", wasted);
        fields.put("statistics.undoWaste", undoWaste);
        fields.put("statistics.regionEndWaste", regionEndWaste);
        fields.put("statistics.failureWaste", failureWaste);
        fields.put("statistics.directAllocated", 0);
        fields.put("statistics.regionsRefilled", 3);
        fields.put("statistics.numPlabsFilled", 7);
        fields.put("statistics.failureUsed", 0);
        return new GenericRecord(
                type, "G1 Evacuation", Instant.EPOCH, Duration.ofMillis(1),
                Duration.ZERO, null, null, 0, 0, fields);
    }

    @Test
    @DisplayName("Maps generation, sums total waste, computes waste %, orders by gcId then Young-before-Old")
    void aggregates() {
        G1PlabStatisticsBuilder builder = new G1PlabStatisticsBuilder();
        // gcId 2 old, gcId 1 young — order must come out 1-Young then 2-Old.
        builder.onRecord(evacuation(Type.G1_EVACUATION_OLD_STATISTICS, 2, 1000, 600, 100, 50, 50, 0));
        builder.onRecord(evacuation(Type.G1_EVACUATION_YOUNG_STATISTICS, 1, 1000, 700, 100, 0, 0, 100));

        List<G1PlabStatistics> result = builder.build();

        assertEquals(2, result.size());

        G1PlabStatistics young = result.getFirst();
        assertEquals(1, young.gcId());
        assertEquals(G1PlabStatistics.YOUNG, young.generation());
        // wasted 100 + undo 0 + regionEnd 0 + failureWaste 100 = 200 of 1000 = 20%.
        assertEquals(200, young.totalWasted());
        assertEquals(20.0, young.wastePercent(), 0.001);
        assertEquals(100, young.failureWaste());

        G1PlabStatistics old = result.get(1);
        assertEquals(2, old.gcId());
        assertEquals(G1PlabStatistics.OLD, old.generation());
        // 100 + 50 + 50 + 0 = 200 of 1000 = 20%.
        assertEquals(200, old.totalWasted());
        assertEquals(20.0, old.wastePercent(), 0.001);
    }

    @Test
    @DisplayName("Zero allocated yields 0% rather than dividing by zero")
    void zeroAllocated() {
        G1PlabStatisticsBuilder builder = new G1PlabStatisticsBuilder();
        builder.onRecord(evacuation(Type.G1_EVACUATION_YOUNG_STATISTICS, 1, 0, 0, 0, 0, 0, 0));

        assertEquals(0.0, builder.build().getFirst().wastePercent(), 0.001);
    }
}
