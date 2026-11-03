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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walks Microscope the way an agent does: from {@code connector_status}, calling every call an
 * answer suggests — its {@code followUp.nextTools}, an investigation area's opening calls, a
 * finding's next tool — with exactly the arguments the answer gave, until no answer suggests a call
 * not yet made. Every suggested call must answer, match its advertised schema, and every page it
 * links must be one the UI serves.
 *
 * <p>A suggestion is a promise: an agent follows it without checking it, so one that fails, or
 * that names an argument the tool refuses, sends the agent into a dead end on a real recording.
 */
class McpFollowUpWalkIntegrationTest extends LocalVolumeMicroscope {

    /** Calls a walk never makes: one removes what the walk reads, the other stops what it waits on. */
    private static final Set<String> NOT_FOLLOWED = Set.of("recordings_delete", "operations_cancel");

    /** Far above what the walk takes today; a walk past it no longer converges. */
    private static final int MAX_CALLS = 500;

    /**
     * Every tool an agent reaches from this recording without typing an argument of its own. Pinned
     * so that a walk that stops early — a profile that never builds, a suggestion dropped — fails
     * rather than passing with nothing left to check, and so that a suggestion added shows here.
     * What it leaves out is what the recording does not hold (no traces, HTTP, JDBC, I/O events, no
     * heap dump) or what takes an argument nobody suggests (a query, a second profile).
     */
    private static final Set<String> REACHED = Set.of(
            "connector_status", "connector_sessions", "connector_files", "connector_download",
            "recordings_analyzeRecording", "profiles_summary",
            "flamegraph_list", "flamegraph_export", "memory_allocations", "blocking_overview", "blocking_monitors",
            "timeline_hotWindows", "timeline_zoom",
            "jfr_listEventTypes", "jfr_describeEventType", "jfr_queryEvents",
            "jvm_autoAnalysis", "jvm_sections", "jvm_system", "jvm_configuration", "jvm_flags", "jvm_container",
            "jvm_gc", "jvm_jit", "jvm_threads", "jvm_nativeMemory");

    private static final String FIELD_TOOL = "tool";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_WHY = "why";

    @Test
    void everyCallAnAnswerSuggestsAnswersInItsTurn() {
        Walk walk = new Walk();
        walk.seed(CONNECTOR_STATUS, Json.createObject(), "the walk's start");
        // Which part of a session to bring is the user's choice, so no answer suggests a download
        // as a call: these two are the choices an agent makes after asking.
        walk.seed(CONNECTOR_DOWNLOAD, Json.createObject().put("sessionRef", recordedSessionRef())
                .put("window", "LAST_MINUTES").put("minutes", 30), "the user's window");
        walk.seed(CONNECTOR_DOWNLOAD, Json.createObject()
                .put("sessionRef", sessionRow(Json.createObject(), CUT_SHORT_SESSION).path("sessionRef").asString())
                .put("window", "WHOLE"), "the user's choice of a session cut short");

        walk.run();

        assertTrue(walk.calls < MAX_CALLS, "The walk did not converge within " + MAX_CALLS + " calls");
        assertTrue(walk.failures.isEmpty(), () -> walk.failures.size() + " of " + walk.calls
                + " suggested calls failed:\n\n" + String.join("\n\n", walk.failures));
        assertEquals(new TreeSet<>(REACHED), walk.toolsReached);
    }

    /** One call, as an answer suggested it, and the answer that did. */
    private record Call(String tool, ObjectNode arguments, String suggestedBy) {

        /** Two suggestions of one tool with one set of arguments are one call, whoever made them. */
        String key() {
            return tool + " " + arguments;
        }
    }

    private final class Walk {

        private final Deque<Call> pending = new ArrayDeque<>();
        private final Set<String> seen = new HashSet<>();
        private final Set<String> toolsReached = new TreeSet<>();
        private final List<String> failures = new ArrayList<>();
        private int calls;

        void seed(String tool, ObjectNode arguments, String why) {
            offer(new Call(tool, arguments, why));
        }

        void run() {
            while (!pending.isEmpty() && calls < MAX_CALLS) {
                Call call = pending.poll();
                calls++;
                toolsReached.add(call.tool());
                JsonNode answer;
                try {
                    answer = microscope.answer(call.tool(), call.arguments());
                    MicroscopeClient.assertLinksResolve(answer);
                } catch (AssertionError e) {
                    failures.add(call.key() + "\n  suggested by: " + call.suggestedBy() + "\n  " + e.getMessage());
                    continue;
                }
                collectSuggestions(answer, call.key());
            }
        }

        private void offer(Call call) {
            if (!NOT_FOLLOWED.contains(call.tool()) && seen.add(call.key())) {
                pending.add(call);
            }
        }

        /** Every suggested call anywhere in the answer: an object naming a tool, its arguments and why. */
        private void collectSuggestions(JsonNode node, String suggestedBy) {
            if (node.isObject()) {
                if (node.path(FIELD_TOOL).isString() && node.path(FIELD_ARGUMENTS).isObject()
                        && node.has(FIELD_WHY)) {
                    offer(new Call(node.path(FIELD_TOOL).asString(),
                            (ObjectNode) node.path(FIELD_ARGUMENTS).deepCopy(), suggestedBy));
                }
                node.properties().forEach(field -> collectSuggestions(field.getValue(), suggestedBy));
            } else if (node.isArray()) {
                node.forEach(element -> collectSuggestions(element, suggestedBy));
            }
        }
    }
}
