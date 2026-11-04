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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walks Microscope the way an agent does: from the calls it is seeded with, calling every call an
 * answer suggests — its {@code followUp.nextTools}, an investigation area's opening calls, a
 * finding's next tool — with exactly the arguments the answer gave, until no answer suggests a call
 * not yet made. Every call must answer, match its advertised schema, and every page it links must
 * be one the UI serves.
 *
 * <p>A suggestion is a promise: an agent follows it without checking it, so one that fails, or that
 * names an argument the tool refuses, sends the agent into a dead end on a real recording.
 */
final class FollowUpWalk {

    /** Calls a walk never makes: one removes what the walk reads, the other stops what it waits on. */
    private static final Set<String> NOT_FOLLOWED = Set.of("recordings_delete", "operations_cancel");

    /** Far above what any walk takes today; a walk past it no longer converges. */
    private static final int MAX_CALLS = 1_000;

    private static final String FIELD_TOOL = "tool";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_WHY = "why";

    /** The argument a paged answer continues by: one page more of the same query. */
    private static final String CURSOR = "cursor";

    private final MicroscopeClient microscope;
    private final Deque<Call> pending = new ArrayDeque<>();
    private final Set<String> seen = new HashSet<>();
    private final SortedSet<String> toolsReached = new TreeSet<>();
    private final Map<String, Integer> callsByTool = new TreeMap<>();
    private final List<String> failures = new ArrayList<>();
    private int calls;

    FollowUpWalk(MicroscopeClient microscope) {
        this.microscope = microscope;
    }

    /** A call the walk starts from: one the agent makes without being told to, such as the user's choice. */
    FollowUpWalk seed(String tool, ObjectNode arguments, String why) {
        offer(new Call(tool, arguments, why));
        return this;
    }

    /**
     * Walks until no answer suggests a new call, and fails naming every suggested call that did not
     * answer and the answer that suggested it.
     *
     * @return every tool the walk reached
     */
    SortedSet<String> run() {
        while (!pending.isEmpty() && calls < MAX_CALLS) {
            Call call = pending.poll();
            calls++;
            toolsReached.add(call.tool());
            callsByTool.merge(call.tool(), 1, Integer::sum);
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
        assertTrue(calls < MAX_CALLS, () -> "The walk did not converge within " + MAX_CALLS + " calls: " + callsByTool);
        assertTrue(failures.isEmpty(), () -> failures.size() + " of " + calls
                + " suggested calls failed:\n\n" + String.join("\n\n", failures));
        return toolsReached;
    }

    private void offer(Call call) {
        if (!NOT_FOLLOWED.contains(call.tool()) && seen.add(call.key())) {
            pending.add(call);
        }
    }

    /** Every suggested call anywhere in the answer: an object naming a tool, its arguments and why. */
    private void collectSuggestions(JsonNode node, String suggestedBy) {
        if (node.isObject()) {
            if (node.path(FIELD_TOOL).isString() && node.path(FIELD_ARGUMENTS).isObject() && node.has(FIELD_WHY)) {
                offer(new Call(node.path(FIELD_TOOL).asString(),
                        (ObjectNode) node.path(FIELD_ARGUMENTS).deepCopy(), suggestedBy));
            }
            node.properties().forEach(field -> collectSuggestions(field.getValue(), suggestedBy));
        } else if (node.isArray()) {
            node.forEach(element -> collectSuggestions(element, suggestedBy));
        }
    }

    /** One call, as an answer suggested it, and the answer that did. */
    private record Call(String tool, ObjectNode arguments, String suggestedBy) {

        /**
         * Two suggestions of one tool with one set of arguments are one call, whoever made them. A
         * continuation is keyed by its query alone: the walk follows one page past the first, which is
         * what proves a cursor works — every page of a real recording's thousands of matches would only
         * repeat it.
         */
        String key() {
            if (arguments.has(CURSOR)) {
                ObjectNode query = arguments.deepCopy();
                query.remove(CURSOR);
                return tool + " " + query + " continued";
            }
            return tool + " " + arguments;
        }
    }
}
