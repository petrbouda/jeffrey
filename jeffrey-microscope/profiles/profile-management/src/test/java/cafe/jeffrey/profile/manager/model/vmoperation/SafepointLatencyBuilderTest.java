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

package cafe.jeffrey.profile.manager.model.vmoperation;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SafepointLatencyBuilder")
class SafepointLatencyBuilderTest {

    private static final String IN_JAVA = "_thread_in_Java";
    private static final String IN_NATIVE = "_thread_in_native";

    /**
     * One thread's measurement for one safepoint — the granularity JFR actually writes at. A JDK 26
     * probe with 16 safepoints emitted 584 of these, which is what makes the aggregation the point
     * of the builder rather than a convenience.
     */
    private static GenericRecord latency(String threadName, String threadState, long nanos) {
        ObjectNode fields = Json.createObject();
        fields.put("threadState", threadState);
        return new GenericRecord(
                Type.SAFEPOINT_LATENCY, "Safepoint Latency", Instant.EPOCH, Duration.ofMillis(1),
                Duration.ofNanos(nanos), new JfrThreadImpl(1, 2, threadName, false), null, 0, 0, fields);
    }

    @Test
    @DisplayName("aggregates per thread rather than listing every measurement")
    void aggregatesPerThread() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        builder.onRecord(latency("laggard", IN_JAVA, 100));
        builder.onRecord(latency("laggard", IN_JAVA, 300));
        builder.onRecord(latency("prompt", IN_JAVA, 10));

        SafepointLatencyData data = builder.build();

        assertEquals(2, data.threadCount());
        assertEquals(2, data.offenders().size());
        SafepointOffender laggard = data.offenders().getFirst();
        assertEquals("laggard", laggard.threadName());
        assertEquals(2, laggard.count());
        assertEquals(400, laggard.totalNanos());
        assertEquals(300, laggard.maxNanos());
    }

    @Test
    @DisplayName("ranks by summed latency, so a habit outranks a one-off")
    void ranksByTotalNotMax() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        // One thread was very slow once; the other is a little slow every single time. The second is
        // the tuning problem, and sorting by max would bury it under the hiccup.
        builder.onRecord(latency("hiccup", IN_JAVA, 900));
        for (int i = 0; i < 20; i++) {
            builder.onRecord(latency("habit", IN_JAVA, 100));
        }

        SafepointLatencyData data = builder.build();

        assertEquals("habit", data.offenders().getFirst().threadName());
        assertEquals(2000, data.offenders().getFirst().totalNanos());
        assertEquals(900, data.worstNanos(), "the single worst sample is still reported");
    }

    @Test
    @DisplayName("describes a thread by the state of its worst measurement")
    void keepsTheStateOfTheWorstSample() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        builder.onRecord(latency("mixed", IN_JAVA, 50));
        builder.onRecord(latency("mixed", IN_NATIVE, 500));
        builder.onRecord(latency("mixed", IN_JAVA, 60));

        // The sample that actually held the JVM up is the one the reader is being asked to act on.
        assertEquals(IN_NATIVE, builder.build().offenders().getFirst().threadState());
    }

    @Test
    @DisplayName("sums overlapping waits as a ranking weight, not as elapsed time")
    void totalIsARankingWeight() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        builder.onRecord(latency("a", IN_JAVA, 100));
        builder.onRecord(latency("b", IN_JAVA, 100));

        // Both threads waited through the same safepoint concurrently. 200ns of summed latency is
        // not 200ns of elapsed time, and nothing downstream may read it as such.
        assertEquals(200, builder.build().totalNanos());
    }

    @Test
    @DisplayName("ignores a measurement the recording never made")
    void ignoresMissingDurations() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        builder.onRecord(new GenericRecord(
                Type.SAFEPOINT_LATENCY, "Safepoint Latency", Instant.EPOCH, Duration.ofMillis(1),
                null, new JfrThreadImpl(1, 2, "unmeasured", false), null, 0, 0, Json.createObject()));

        assertEquals(SafepointLatencyData.EMPTY, builder.build());
    }

    @Test
    @DisplayName("falls back rather than dropping a thread with no state recorded")
    void toleratesMissingThreadState() {
        SafepointLatencyBuilder builder = new SafepointLatencyBuilder();
        builder.onRecord(new GenericRecord(
                Type.SAFEPOINT_LATENCY, "Safepoint Latency", Instant.EPOCH, Duration.ofMillis(1),
                Duration.ofNanos(70), new JfrThreadImpl(1, 2, "stateless", false), null, 0, 0,
                Json.createObject()));

        SafepointOffender offender = builder.build().offenders().getFirst();
        assertEquals("stateless", offender.threadName());
        assertTrue(offender.threadState() != null && !offender.threadState().isBlank());
    }
}
