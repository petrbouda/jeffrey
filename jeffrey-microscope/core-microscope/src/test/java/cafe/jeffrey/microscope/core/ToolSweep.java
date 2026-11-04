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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls every tool an agent can call with nothing but the profile it has, on one profile: each
 * answers in its advertised schema with pages the UI serves, or refuses in a sentence the agent can
 * act on — never with the server's internal failure. The tools come from {@code tools/list}, so a
 * tool added later is swept without a change here.
 */
final class ToolSweep {

    private static final String PROFILE_ID = "profileId";
    private static final String HEAP_FAMILY = "heap_";
    /** The heap family's probe: it reports what a profile holds rather than requiring a heap dump. */
    private static final String HEAP_PROBE = "heap_status";
    private static final String INTERNAL_FAILURE = "The tool failed inside Jeffrey";

    private ToolSweep() {
    }

    /**
     * Sweeps a profile built from a recording, which may refuse exactly the heap tools but their
     * probe, each saying the profile is not a heap dump.
     */
    static void assertEveryToolAnswersARecording(MicroscopeClient microscope, String profileId) {
        Sweep sweep = sweep(microscope, profileId);

        assertFalse(sweep.heapTools().isEmpty(), "The sweep reached no heap tool");
        assertEquals(sweep.heapTools(), sweep.refusals().keySet(), () -> "Refused: " + sweep.refusals());
        String notAHeapDump = "Profile " + profileId + " is not a heap dump";
        sweep.refusals().forEach((tool, text) ->
                assertTrue(text.contains(notAHeapDump), () -> tool + ": " + text));
    }

    /**
     * Sweeps a profile built from a prepared heap dump: every heap tool answers, and a tool that reads
     * a recording refuses in a sentence.
     */
    static void assertEveryToolAnswersAHeapDump(MicroscopeClient microscope, String profileId) {
        Sweep sweep = sweep(microscope, profileId);

        assertFalse(sweep.heapTools().isEmpty(), "The sweep reached no heap tool");
        Set<String> refusedHeapTools = new TreeSet<>(sweep.heapTools());
        refusedHeapTools.retainAll(sweep.refusals().keySet());
        assertEquals(Set.of(), refusedHeapTools, () -> "Refused: " + sweep.refusals());
    }

    /** Every tool callable with the profile alone, called: what refused, and no answer an internal failure. */
    private static Sweep sweep(MicroscopeClient microscope, String profileId) {
        Set<String> heapTools = new TreeSet<>();
        Map<String, String> refusals = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, JsonNode> tool : microscope.tools().entrySet()) {
            JsonNode input = tool.getValue().path("inputSchema");
            if (!needsAtMostTheProfile(input)) {
                continue;
            }
            if (tool.getKey().startsWith(HEAP_FAMILY) && !HEAP_PROBE.equals(tool.getKey())) {
                heapTools.add(tool.getKey());
            }
            ObjectNode arguments = Json.createObject();
            if (input.path("properties").has(PROFILE_ID)) {
                arguments.put(PROFILE_ID, profileId);
            }
            try {
                JsonNode result = microscope.result(tool.getKey(), arguments);
                if (result.path("isError").asBoolean()) {
                    refusals.put(tool.getKey(), result.path("content").get(0).path("text").asString());
                } else {
                    MicroscopeClient.assertLinksResolve(result.path("structuredContent"));
                }
            } catch (AssertionError e) {
                failures.add(tool.getKey() + ": " + e.getMessage());
            }
        }

        assertTrue(failures.isEmpty(), () -> String.join("\n\n", failures));
        refusals.forEach((tool, text) -> assertFalse(text.startsWith(INTERNAL_FAILURE), () -> tool + ": " + text));
        return new Sweep(heapTools, refusals);
    }

    /** What a sweep called of the heap family, and what refused. */
    private record Sweep(Set<String> heapTools, Map<String, String> refusals) {
    }

    /** No argument required beyond the profile: what an agent can call before it has chosen anything. */
    private static boolean needsAtMostTheProfile(JsonNode inputSchema) {
        for (JsonNode required : inputSchema.path("required")) {
            if (!PROFILE_ID.equals(required.asString())) {
                return false;
            }
        }
        return true;
    }
}
