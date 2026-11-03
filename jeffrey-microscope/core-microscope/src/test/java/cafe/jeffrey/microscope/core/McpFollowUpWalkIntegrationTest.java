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

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@link FollowUpWalk} over the recorded session: from {@code connector_status}, every call an
 * answer suggests answers in its turn.
 */
class McpFollowUpWalkIntegrationTest extends LocalVolumeMicroscope {

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

    @Test
    void everyCallAnAnswerSuggestsAnswersInItsTurn() {
        // Which part of a session to bring is the user's choice, so no answer suggests a download
        // as a call: these two are the choices an agent makes after asking.
        String cutShortRef = sessionRow(Json.createObject(), CUT_SHORT_SESSION).path("sessionRef").asString();
        FollowUpWalk walk = new FollowUpWalk(microscope)
                .seed(CONNECTOR_STATUS, Json.createObject(), "the walk's start")
                .seed(CONNECTOR_DOWNLOAD, Json.createObject().put("sessionRef", recordedSessionRef())
                        .put("window", "LAST_MINUTES").put("minutes", 30), "the user's window")
                .seed(CONNECTOR_DOWNLOAD, Json.createObject().put("sessionRef", cutShortRef).put("window", "WHOLE"),
                        "the user's choice of a session cut short");

        assertEquals(new TreeSet<>(REACHED), walk.run());
    }
}
