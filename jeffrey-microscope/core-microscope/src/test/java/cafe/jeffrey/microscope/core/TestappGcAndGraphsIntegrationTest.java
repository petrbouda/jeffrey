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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.flamegraph.proto.Frame;
import cafe.jeffrey.flamegraph.proto.GraphData;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.TestappRecording;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static cafe.jeffrey.test.recording.TestappRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.DOM;
import static cafe.jeffrey.test.recording.TestappRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.TestappRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readings of the testapp runs no other test makes on real data: the garbage collector's dashboard,
 * held to the JDK's own collection events, and the flamegraphs the UI draws — the protobuf the page
 * decodes, not the Markdown an agent reads — held to the pinned counts.
 */
class TestappGcAndGraphsIntegrationTest extends TestappVolumeMicroscope {

    private static final String GARBAGE_COLLECTION = "jdk.GarbageCollection";
    private static final String YOUNG_COLLECTION = "jdk.YoungGarbageCollection";
    private static final String HEAP_SUMMARY = "jdk.GCHeapSummary";
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    /** The dashboard rounds its milliseconds; a figure is held to the events within this much. */
    private static final double MILLIS_PRECISION = 0.01;

    /** The garbage collector, as {@code jvm_gc} reports it against the events it read. */
    @Nested
    class GarbageCollection {

        /** Every collection is counted, the young ones as many as the young-collection events and the perf counters say. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everyCollectionIsCounted(TestappRecording recording) {
            JsonNode budget = gc(recording).path("dashboard").path("pauseBudget");

            assertEquals(count(recording, GARBAGE_COLLECTION), budget.path("collections").asInt(), budget::toString);
            assertEquals(count(recording, YOUNG_COLLECTION), budget.path("youngCollections").asInt(), budget::toString);
            assertEquals(recording.perfCounterFigures().youngCollections(), budget.path("youngCollections").asInt());
        }

        /** The pauses the collections took, summed and the longest, are the events' own. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void thePausesAreTheCollectionsOwn(TestappRecording recording) {
            JsonNode budget = gc(recording).path("dashboard").path("pauseBudget");
            List<String> pauses = query(recording, "SELECT sum(json_extract_string(fields, '$.sumOfPauses')::BIGINT),"
                    + " max(json_extract_string(fields, '$.longestPause')::BIGINT) FROM events WHERE event_type = '"
                    + GARBAGE_COLLECTION + "'").getFirst();

            assertClose(Long.parseLong(pauses.get(0)) / NANOS_PER_MILLI, budget.path("totalPauseMs").asDouble());
            assertClose(Long.parseLong(pauses.get(1)) / NANOS_PER_MILLI, budget.path("longestPauseMs").asDouble());
        }

        /** What a collection freed is its heap before less its heap after, by the summaries the collection wrote. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void whatACollectionFreedIsItsHeapBeforeLessAfter(TestappRecording recording) {
            JsonNode longest = gc(recording).path("dashboard").path("longestCollections").path(0);
            List<String> heap = query(recording, "SELECT max(CASE WHEN json_extract_string(fields, '$.when') = 'Before GC'"
                    + " THEN json_extract_string(fields, '$.heapUsed')::BIGINT END),"
                    + " max(CASE WHEN json_extract_string(fields, '$.when') = 'After GC'"
                    + " THEN json_extract_string(fields, '$.heapUsed')::BIGINT END)"
                    + " FROM events WHERE event_type = '" + HEAP_SUMMARY + "'"
                    + " AND json_extract_string(fields, '$.gcId')::BIGINT = " + longest.path("gcId").asLong()).getFirst();

            assertEquals(Long.parseLong(heap.get(0)), longest.path("heapBeforeBytes").asLong(), longest::toString);
            assertEquals(Long.parseLong(heap.get(1)), longest.path("heapAfterBytes").asLong(), longest::toString);
            assertEquals(longest.path("heapBeforeBytes").asLong() - longest.path("heapAfterBytes").asLong(),
                    longest.path("freedBytes").asLong());
        }

        private JsonNode gc(TestappRecording recording) {
            return answer("jvm_gc", recording, Json.createObject());
        }

        private int count(TestappRecording recording, String eventType) {
            return Integer.parseInt(scalar(recording, "SELECT count(*) FROM events WHERE event_type = '" + eventType + "'"));
        }
    }

    /** The flamegraphs the UI draws, decoded as its page decodes them. */
    @Nested
    class TheUiGraphs {

        /** The root holds every sample the run took: CPU, and wall clock counted in samples. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theRootHoldsEverySample(TestappRecording recording) {
            assertEquals(recording.counts().executionSamples(), root(graph(recording, EXECUTION_SAMPLE, false, false)).getTotalSamples());
            assertEquals(recording.counts().wallClockSamples(), root(graph(recording, WALL_CLOCK_SAMPLE, false, false)).getTotalSamples());
        }

        /** Weighted, the allocation graph's root holds every byte the samples stand for. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theAllocationRootHoldsEveryByte(TestappRecording recording) {
            Frame root = root(graph(recording, ALLOCATION_IN_NEW_TLAB, false, true));

            assertEquals(recording.counts().allocationSamples(), root.getTotalSamples());
            assertEquals(Long.parseLong(scalar(recording, "SELECT sum(weight) FROM events WHERE event_type = '"
                    + ALLOCATION_IN_NEW_TLAB + "'")), root.getTotalWeight());
        }

        /** Split per thread, the threads under the root hold every sample between them. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theThreadsHoldEverySampleBetweenThem(TestappRecording recording) {
            GraphData graph = graph(recording, WALL_CLOCK_SAMPLE, true, false);
            long threads = graph.getFlamegraph().getLevels(1).getFramesList().stream().mapToLong(Frame::getTotalSamples).sum();

            assertEquals(recording.counts().wallClockSamples(), threads);
        }

        /** The differential graph's root holds the DOM run's samples, and how many more they are than the direct run's. */
        @Test
        void theDifferentialRootHoldsBothRuns() {
            byte[] bytes = microscope.postForBytes("/api/internal/profiles/" + profile(DOM) + "/diff/" + profile(DIRECT)
                    + "/differential-flamegraph", request(EXECUTION_SAMPLE, false, false));
            Frame root = root(parse(bytes));

            assertEquals((long) DOM.counts().executionSamples() - DIRECT.counts().executionSamples(),
                    root.getDiffDetails().getSamples(), root::toString);
        }

        private GraphData graph(TestappRecording recording, String eventType, boolean threadMode, boolean weight) {
            return parse(microscope.postForBytes("/api/internal/profiles/" + profile(recording) + "/flamegraph",
                    request(eventType, threadMode, weight)));
        }

        private ObjectNode request(String eventType, boolean threadMode, boolean weight) {
            return Json.createObject()
                    .put("eventType", eventType)
                    .put("useThreadMode", threadMode)
                    .put("useWeight", weight)
                    .put("excludeNonJavaSamples", false)
                    .put("excludeIdleSamples", false)
                    .put("onlyUnsafeAllocationSamples", false)
                    .put("components", "FLAMEGRAPH_ONLY");
        }

        private Frame root(GraphData graph) {
            assertTrue(graph.getFlamegraph().getLevelsCount() > 0, "The graph has no levels");
            return graph.getFlamegraph().getLevels(0).getFrames(0);
        }

        private GraphData parse(byte[] bytes) {
            try {
                return GraphData.parseFrom(bytes);
            } catch (InvalidProtocolBufferException e) {
                throw new AssertionError("Not a graph: " + e.getMessage(), e);
            }
        }
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) <= MILLIS_PRECISION, () -> actual + " against " + expected);
    }
}
