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
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.RecordedSession.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.RecordedSession.CHUNKS;
import static cafe.jeffrey.test.recording.RecordedSession.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.RecordedSession.MONITOR_ENTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two halves of the recorded session compared as an agent compares a run before a change with
 * one after it: the second half as the primary, the first as its baseline. Each side's figures are
 * what its two chunks hold, and the one contended lock of the session — in the first half only —
 * is the one event type the two do not share.
 */
class McpCompareIntegrationTest extends LocalVolumeMicroscope {

    private static final List<Chunk> FIRST_HALF = CHUNKS.subList(0, 2);
    private static final List<Chunk> SECOND_HALF = CHUNKS.subList(2, 4);

    private static final Pattern PRIMARY_TOTAL = Pattern.compile("(?m)^primary_total: (\\d+)$");
    private static final Pattern BASELINE_TOTAL = Pattern.compile("(?m)^baseline_total: (\\d+)");

    /** Built once: every test here compares the same two profiles. */
    private static String firstHalf;
    private static String secondHalf;

    private void profiles() {
        if (secondHalf == null) {
            secondHalf = profileOf(download(Json.createObject().put("window", "LAST_MINUTES").put("minutes", 30)));
            firstHalf = profileOf(download(Json.createObject().put("window", "CUSTOM")
                    .put("startEpochMs", FIRST_HALF.getFirst().openedAt().toEpochMilli())
                    .put("endEpochMs", SECOND_HALF.getFirst().openedAt().toEpochMilli())));
        }
    }

    @Test
    void theTwoHalvesAreComparedOnWhatEachHolds() {
        profiles();

        JsonNode list = microscope.answer("compare_list", pair(secondHalf, firstHalf));

        assertEquals("OK", list.path("status").asString());
        JsonNode cpu = comparable(list, EXECUTION_SAMPLE);
        assertEquals(RecordedSession.executionSamples(SECOND_HALF), cpu.path("primarySamples").asInt());
        assertEquals(RecordedSession.executionSamples(FIRST_HALF), cpu.path("baselineSamples").asInt());
        JsonNode allocation = comparable(list, ALLOCATION_IN_NEW_TLAB);
        assertEquals(RecordedSession.allocationSamples(SECOND_HALF), allocation.path("primarySamples").asInt());
        assertEquals(RecordedSession.allocationSamples(FIRST_HALF), allocation.path("baselineSamples").asInt());
    }

    /** The contended lock is in the first half only: the type is the baseline's alone, either way round. */
    @Test
    void theLockOnlyTheFirstHalfContendedIsTheBaselinesAloneAndThePrimarysWhenSwapped() {
        profiles();
        assertEquals(0, RecordedSession.monitorEnters(SECOND_HALF));
        assertTrue(RecordedSession.monitorEnters(FIRST_HALF) > 0);

        JsonNode forward = microscope.answer("compare_list", pair(secondHalf, firstHalf));
        JsonNode swapped = microscope.answer("compare_list", pair(firstHalf, secondHalf));

        assertEquals(List.of(MONITOR_ENTER), strings(forward.path("onlyInBaseline")));
        assertEquals(List.of(), strings(forward.path("onlyInPrimary")));
        assertEquals(List.of(MONITOR_ENTER), strings(swapped.path("onlyInPrimary")));
        assertEquals(List.of(), strings(swapped.path("onlyInBaseline")));
    }

    /** Both documents the agent reads state each side's total, and the halves are as long, so unscaled. */
    @Test
    void theMovementsAndTheDifferentialGraphTotalEachSide() {
        profiles();

        for (String tool : List.of("compare_movements", "compare_flamegraph")) {
            JsonNode answer = microscope.answer(tool, pair(secondHalf, firstHalf).put("eventType", EXECUTION_SAMPLE));
            String document = answer.path("markdown").asString();

            assertFalse(answer.path("truncated").asBoolean(), tool);
            assertEquals(RecordedSession.executionSamples(SECOND_HALF), total(PRIMARY_TOTAL, document, tool));
            assertEquals(RecordedSession.executionSamples(FIRST_HALF), total(BASELINE_TOTAL, document, tool));
        }
    }

    /** The quality check names each side by its own profile, so a reader can tell which run is which. */
    @Test
    void theQualityCheckNamesEachSide() {
        profiles();

        JsonNode quality = microscope.answer("compare_quality", pair(secondHalf, firstHalf));

        assertEquals(secondHalf, quality.path("primary").path("profile").path("profileId").asString());
        assertEquals(firstHalf, quality.path("baseline").path("profile").path("profileId").asString());
    }

    private static ObjectNode pair(String primary, String baseline) {
        return Json.createObject().put("profileId", primary).put("baselineProfileId", baseline);
    }

    private static JsonNode comparable(JsonNode list, String eventType) {
        for (JsonNode type : list.path("comparable")) {
            if (eventType.equals(type.path("eventType").asString())) {
                return type;
            }
        }
        throw new AssertionError("compare_list cannot compare " + eventType + ": " + list);
    }

    private static int total(Pattern header, String document, String tool) {
        Matcher matcher = header.matcher(document);
        assertTrue(matcher.find(), () -> tool + "'s document states no " + header.pattern());
        return Integer.parseInt(matcher.group(1));
    }
}
