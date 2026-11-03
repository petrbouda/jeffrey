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

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.RecordedSession;
import cafe.jeffrey.test.recording.RecordedSession.Chunk;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.RecordedSession.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.RecordedSession.CHUNKS;
import static cafe.jeffrey.test.recording.RecordedSession.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.RecordedSession.THREAD_PARK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the analysis answers say about the last half hour of the recorded session, held to what its
 * two chunks hold: every count below is the chunks' own, as {@code jfr summary} reads them, and
 * every total one tool reports agrees with the same total another reports.
 */
class McpAnswerFiguresIntegrationTest extends LocalVolumeMicroscope {

    private static final List<Chunk> LAST_HALF_HOUR = CHUNKS.subList(2, 4);
    private static final int CPU_SAMPLES = RecordedSession.executionSamples(LAST_HALF_HOUR);
    private static final int ALLOCATION_SAMPLES = RecordedSession.allocationSamples(LAST_HALF_HOUR);
    private static final int THREAD_PARKS = RecordedSession.threadParks(LAST_HALF_HOUR);

    /** The total in the header of a flamegraph export's document. */
    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total: (\\d+)$");
    private static final String INTERNAL_FAILURE = "The tool failed inside Jeffrey";

    /** Built once: every test here reads the same profile. */
    private static String profileId;

    private String profile() {
        if (profileId == null) {
            profileId = profileOf(download(Json.createObject().put("window", "LAST_MINUTES").put("minutes", 30)));
        }
        return profileId;
    }

    @Test
    void theFlamegraphCatalogueCountsWhatTheChunksHold() {
        JsonNode catalogue = microscope.answer("flamegraph_list", Json.createObject().put("profileId", profile()));

        assertEquals(CPU_SAMPLES, graphable(catalogue, EXECUTION_SAMPLE).path("samples").asInt());
        assertEquals(ALLOCATION_SAMPLES, graphable(catalogue, ALLOCATION_IN_NEW_TLAB).path("samples").asInt());
        assertEquals(THREAD_PARKS, graphable(catalogue, THREAD_PARK).path("samples").asInt());
    }

    /** The document the agent reads states the same total the catalogue counts. */
    @Test
    void theCpuFlamegraphTotalsEveryCpuSample() {
        JsonNode export = microscope.answer("flamegraph_export", Json.createObject().put("profileId", profile())
                .put("eventType", EXECUTION_SAMPLE).put("detail", "SUMMARY"));

        assertEquals("OK", export.path("status").asString());
        assertFalse(export.path("truncated").asBoolean());
        Matcher total = SAMPLES_TOTAL.matcher(export.path("markdown").asString());
        assertTrue(total.find(), "The export's header states no samples_total");
        assertEquals(CPU_SAMPLES, Integer.parseInt(total.group(1)));
    }

    /** The timeline spreads the same samples over exactly the profile's span. */
    @Test
    void theTimelineCoversTheProfileAndHoldsEveryCpuSample() {
        JsonNode summary = microscope.answer(PROFILES_SUMMARY, Json.createObject().put("profileId", profile()));
        JsonNode timeline = microscope.answer("timeline_hotWindows", Json.createObject()
                .put("profileId", profile()).put("eventType", EXECUTION_SAMPLE));

        assertEquals(CPU_SAMPLES, timeline.path("total").asInt());
        assertEquals(summary.path("startedAtEpochMs").asLong(), timeline.path("window").path("startEpochMs").asLong());
        assertEquals(summary.path("finishedAtEpochMs").asLong(), timeline.path("window").path("endEpochMs").asLong());
    }

    /** Every type is listed (none omitted), so the table adds up to its overview, and to the graph's weight. */
    @Test
    void theAllocationTableAddsUpToItsOverview() {
        JsonNode allocations = microscope.answer("memory_allocations", Json.createObject().put("profileId", profile()));
        JsonNode catalogue = microscope.answer("flamegraph_list", Json.createObject().put("profileId", profile()));

        assertEquals(0, allocations.path("omittedTypes").asInt());
        long count = 0;
        long bytes = 0;
        for (JsonNode type : allocations.path("topTypes")) {
            count += type.path("count").asLong();
            bytes += type.path("bytes").asLong();
        }
        long totalBytes = allocations.path("overview").path("totalBytes").asLong();
        assertEquals(ALLOCATION_SAMPLES, count);
        assertEquals(totalBytes, bytes);
        assertEquals(totalBytes, graphable(catalogue, ALLOCATION_IN_NEW_TLAB).path("weight").asLong());
    }

    @Test
    void theBlockingOverviewCountsEveryPark() {
        JsonNode overview = microscope.answer("blocking_overview", Json.createObject().put("profileId", profile()))
                .path("overview");

        assertEquals(THREAD_PARKS, overview.path("parkCount").asInt());
        assertTrue(overview.path("hasParks").asBoolean());
        assertFalse(overview.path("hasMonitorEnter").asBoolean());
    }

    /** The agent's own SQL over the profile's events reads the same counts as the dashboards. */
    @Test
    void sqlOverTheEventsCountsTheSame() {
        JsonNode result = microscope.answer("jfr_executeQuery", Json.createObject().put("profileId", profile())
                .put("query", "SELECT event_type, count(*) FROM events WHERE event_type IN ('"
                        + EXECUTION_SAMPLE + "', '" + ALLOCATION_IN_NEW_TLAB + "', '" + THREAD_PARK + "') "
                        + "GROUP BY event_type ORDER BY event_type"));

        assertEquals("COMPLETE", result.path("truncation").asString());
        assertEquals(List.of(
                        List.of(EXECUTION_SAMPLE, Integer.toString(CPU_SAMPLES)),
                        List.of(ALLOCATION_IN_NEW_TLAB, Integer.toString(ALLOCATION_SAMPLES)),
                        List.of(THREAD_PARK, Integer.toString(THREAD_PARKS))),
                rows(result));
    }

    /**
     * A bucket that does not divide a second once failed inside the heatmap; it is a refusal that
     * names the widths that work, and a width that does answers.
     */
    @Test
    void theZoomRefusesABucketThatDoesNotDivideASecondAndTakesOneThatDoes() {
        JsonNode timeline = microscope.answer("timeline_hotWindows", Json.createObject()
                .put("profileId", profile()).put("eventType", EXECUTION_SAMPLE));
        long start = timeline.path("window").path("startEpochMs").asLong();

        String refusal = microscope.refusal("timeline_zoom", Json.createObject().put("profileId", profile())
                .put("eventType", EXECUTION_SAMPLE).put("startEpochMs", start).put("endEpochMs", start + 180_000)
                .put("bucketMs", 10_000));
        JsonNode zoom = microscope.answer("timeline_zoom", Json.createObject().put("profileId", profile())
                .put("eventType", EXECUTION_SAMPLE).put("startEpochMs", start).put("endEpochMs", start + 180_000)
                .put("bucketMs", 1_000));

        assertFalse(refusal.contains(INTERNAL_FAILURE), refusal);
        assertTrue(refusal.contains("bucketMs 10000 does not divide a second"), refusal);
        assertEquals("OK", zoom.path("status").asString(), zoom::toString);
    }

    private static JsonNode graphable(JsonNode catalogue, String eventType) {
        for (JsonNode available : catalogue.path("available")) {
            if (eventType.equals(available.path("eventType").asString())) {
                return available;
            }
        }
        throw new AssertionError("flamegraph_list does not offer " + eventType + ": " + catalogue);
    }

    private static List<List<String>> rows(JsonNode result) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode row : result.path("rows")) {
            rows.add(strings(row));
        }
        return rows;
    }
}
