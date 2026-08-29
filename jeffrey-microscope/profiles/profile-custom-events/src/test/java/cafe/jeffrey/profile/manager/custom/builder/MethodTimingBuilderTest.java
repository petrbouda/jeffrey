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

package cafe.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MethodTimingBuilder")
class MethodTimingBuilderTest {

    /**
     * One periodic tally. The counters are cumulative over the whole recording, so a later event for
     * the same method is a superset of the earlier one rather than a new slice of work.
     */
    private static GenericRecord timing(String method, long invocations, long min, long avg, long max) {
        ObjectNode fields = Json.createObject();
        fields.put("method", method);
        fields.put("invocations", invocations);
        fields.put("minimum", min);
        fields.put("average", avg);
        fields.put("maximum", max);
        return new GenericRecord(
                Type.METHOD_TIMING, "Method Timing", Instant.EPOCH, Duration.ofMillis(1),
                null, null, null, 0, 0, fields);
    }

    @Test
    @DisplayName("keeps the final tally instead of summing the periodic ones")
    void keepsTheFinalTally() {
        MethodTimingBuilder builder = new MethodTimingBuilder();
        // Measured on a JDK 26 probe: a dump at 5s carried invocations=5000, and one at 10s carried
        // 5000 and 11000 -- the second being the running total, not that chunk's contribution.
        builder.onRecord(timing("app.Service#handle", 5_000, 10, 20, 90));
        builder.onRecord(timing("app.Service#handle", 11_000, 10, 21, 140));

        MethodTimingData data = builder.build();

        assertEquals(1, data.methods().size(), "one row per method, however many chunks reported it");
        MethodTimingStat stat = data.methods().getFirst();
        assertEquals(11_000, stat.invocations(), "the running total, not 16000");
        assertEquals(140, stat.maxNanos(), "the extreme over everything recorded so far");
        assertEquals(21, stat.avgNanos(), "the mean over every call, taken from the final tally");
    }

    @Test
    @DisplayName("is not fooled by tallies arriving out of order")
    void toleratesOutOfOrderTallies() {
        MethodTimingBuilder builder = new MethodTimingBuilder();
        builder.onRecord(timing("app.Service#handle", 11_000, 10, 21, 140));
        builder.onRecord(timing("app.Service#handle", 5_000, 10, 20, 90));

        // The counter only ever grows, so the largest value is the newest whatever order it arrives
        // in -- which is why the builder selects on it rather than on a timestamp.
        assertEquals(11_000, builder.build().methods().getFirst().invocations());
    }

    @Test
    @DisplayName("splits the flattened method into the class and method columns")
    void splitsTheMethodName() {
        MethodTimingBuilder builder = new MethodTimingBuilder();
        builder.onRecord(timing("cafe.jeffrey.probe.TimingProbe#slow", 10, 3_120_000, 3_140_000, 3_210_000));

        MethodTimingStat stat = builder.build().methods().getFirst();
        assertEquals("cafe.jeffrey.probe.TimingProbe", stat.className());
        assertEquals("slow", stat.methodName());
    }

    @Test
    @DisplayName("ranks the most-invoked method first")
    void ranksByInvocations() {
        MethodTimingBuilder builder = new MethodTimingBuilder();
        builder.onRecord(timing("app.A#rare", 10, 1, 2, 3));
        builder.onRecord(timing("app.B#hot", 1_000_000, 1, 2, 3));

        MethodTimingData data = builder.build();
        assertEquals("hot", data.methods().getFirst().methodName());
        assertEquals(1_000_010, data.totalInvocations());
    }

    @Test
    @DisplayName("ignores a tally with no method to attribute it to")
    void ignoresNamelessTallies() {
        MethodTimingBuilder builder = new MethodTimingBuilder();
        ObjectNode fields = Json.createObject();
        fields.put("invocations", 5);
        builder.onRecord(new GenericRecord(
                Type.METHOD_TIMING, "Method Timing", Instant.EPOCH, Duration.ofMillis(1),
                null, null, null, 0, 0, fields));

        assertTrue(builder.build().methods().isEmpty());
        assertEquals(MethodTimingData.EMPTY, builder.build());
    }
}
