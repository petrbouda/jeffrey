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
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.TestappRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.DOM;
import static cafe.jeffrey.test.recording.TestappRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.TestappRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_QUERY;
import static cafe.jeffrey.test.recording.TestappRecording.MONITOR_ENTER;
import static cafe.jeffrey.test.recording.TestappRecording.THREAD_PARK;
import static cafe.jeffrey.test.recording.TestappRecording.TRACE_SPAN;
import static cafe.jeffrey.test.recording.TestappRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microscope over realistic recordings: a service under load, profiled for CPU, wall clock,
 * allocation and locks, with the JDK's own events and the service's traces beside them. What the
 * small session cannot show, these do: wall-clock samples folded into events, every request a trace,
 * the JDBC statements and the pool, and two runs of one service to compare.
 */
class TestappRecordingsIntegrationTest extends TestappVolumeMicroscope {

    private static final Pattern SCALE_FACTOR = Pattern.compile("(?m)^baseline_scale_factor: ([0-9.]+)$");
    /** The SQLite driver's native library, whatever the run named its file. */
    private static final String SQLITE_LIBRARY = "libsqlitejdbc.so";
    /** A movement's change when only one of the runs has the method at all. */
    private static final Set<String> ONE_SIDED = Set.of("new", "gone");
    private static final Pattern SEARCH_MATCHES = Pattern.compile("(?m)^search_matches: (\\d+)");

    /** async-profiler's samplers, as the profile records them: the CPU by its timer, both every 10 ms. */
    private static final String ASYNC_PROFILER_SOURCE = "0";
    private static final String CPU_TIMER = "CTIMER";
    private static final String TEN_MILLIS = "{\"sample_interval\":\"10000000\"}";

    /** Every count each recording's profile reports is the recording's own. */
    @Nested
    class Figures {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theProfileCountsWhatTheRecordingHolds(TestappRecording recording) {
            assertCounts(recording, profile(recording));
        }

        /**
         * async-profiler folds an idle thread's wall-clock samples into one event: SQL over the
         * events counts fewer rows than samples, and the samples are what every dashboard reports.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void wallClockEventsCarryMoreSamplesThanThereAreEvents(TestappRecording recording) {
            assertEquals(List.of(List.of(String.valueOf(recording.counts().wallClockEvents()),
                            String.valueOf(recording.counts().wallClockSamples()))),
                    query(recording, "SELECT count(*), sum(samples) FROM events WHERE event_type = '"
                            + WALL_CLOCK_SAMPLE + "'"));
        }

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theCpuFlamegraphTotalsEveryCpuSample(TestappRecording recording) {
            JsonNode export = answer("flamegraph_export", recording, Json.createObject()
                    .put("eventType", EXECUTION_SAMPLE).put("detail", "SUMMARY"));

            assertEquals(recording.counts().executionSamples(), header(export, "samples_total"));
        }

        /**
         * The samplers are async-profiler's, read from the settings its chain recorded — the CPU by its
         * timer — both at their 10 ms interval, though the JDK's chain recorded settings of its own for
         * the same event types.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theSamplersAreAsyncProfilersAtTheirInterval(TestappRecording recording) {
            for (String profileId : List.of(profile(recording), splitProfile(recording))) {
                assertEquals(List.of(
                                List.of(EXECUTION_SAMPLE, ASYNC_PROFILER_SOURCE, CPU_TIMER, TEN_MILLIS),
                                List.of(WALL_CLOCK_SAMPLE, ASYNC_PROFILER_SOURCE, "null", TEN_MILLIS)),
                        query(profileId, "SELECT name, source, COALESCE(subtype, 'null'), extras FROM event_types"
                                + " WHERE name IN ('" + EXECUTION_SAMPLE + "', '" + WALL_CLOCK_SAMPLE + "') ORDER BY name"));
            }
        }
    }

    /**
     * The same run as two producers writing separate files leave it: the JDK's two chunks and
     * async-profiler's one side by side in a session, named by when each began, the older ones
     * compressed by the hub.
     */
    @Nested
    class SplitSessions {

        private static final String ARCHIVE_SUFFIX = ".lz4";

        /** Nothing joins the files: the session reads as the file they were cut from. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theChunksAddUpToTheFileTheyWereCutFrom(TestappRecording recording) {
            assertCounts(recording, splitProfile(recording));
        }

        /** Every chunk is a recording of the session, compressed as the hub left it but the newest of the newest session. */
        @Test
        void theSessionsListTheirChunksAsTheHubLeftThem() {
            for (TestappRecording recording : TestappRecording.values()) {
                JsonNode files = microscope.answer("connector_files", Json.createObject()
                        .put("sessionRef", sessionRef(SPLIT_SESSIONS.get(recording))));
                List<String> names = new ArrayList<>();
                files.path("files").forEach(file -> names.add(file.path("name").asString()));

                assertEquals(3, names.size(), names::toString);
                long plain = names.stream().filter(name -> !name.endsWith(ARCHIVE_SUFFIX)).count();
                assertEquals(recording == DOM ? 1 : 0, plain, names::toString);
            }
        }
    }

    /**
     * The {@link FollowUpWalk} over both runs, as delivered and split: every call an answer suggests
     * answers in its turn. The tools it reaches are pinned, and against the small session they add
     * what this service holds — its traces, its HTTP endpoints, its JDBC statements and pool.
     */
    @Nested
    class Walk {

        /**
         * What it leaves out is what the runs do not hold (notifications, gRPC, an HTTP client, a heap
         * dump) and what takes an argument nobody suggests (a query, a second profile). The attribute
         * tools are reached from the traces' overview: keys, then a key's values, then a search.
         */
        private static final Set<String> REACHED = Set.of(
                "connector_status", "connector_sessions", "connector_download", "profiles_summary",
                "profiles_features", "flamegraph_list", "flamegraph_export", "code_hotspots", "code_method",
                "memory_allocations",
                "memory_leakCandidates", "blocking_overview", "blocking_monitors", "timeline_hotWindows",
                "timeline_zoom", "io_overview", "io_endpoints", "io_slowest", "http_overview", "http_endpoint",
                "jdbc_overview", "jdbc_pools", "jdbc_statementGroup", "traces_overview", "traces_operations",
                "traces_attributeKeys", "traces_attributeValues", "traces_attributeSearch",
                "traces_operationExport", "traces_operationFlamegraphExport", "traces_slowestTraces",
                "traces_traceExport", "traces_spanFlamegraphExport", "jfr_listEventTypes", "jfr_describeEventType",
                "jfr_queryEvents", "jvm_autoAnalysis", "jvm_system", "jvm_configuration", "jvm_flags",
                "jvm_container", "jvm_gc", "jvm_gcDetail", "jvm_jit", "jvm_threads", "jvm_nativeMemory",
                "jvm_safepoints", "jvm_exceptions", "jvm_exceptionSites");

        /** Every session is analysed first, so a download suggests its profile whatever ran before. */
        @Test
        void everyCallAnAnswerSuggestsAnswersInItsTurn() {
            FollowUpWalk walk = new FollowUpWalk(microscope).seed(CONNECTOR_STATUS, Json.createObject(), "the walk's start");
            for (TestappRecording recording : TestappRecording.values()) {
                profile(recording);
                splitProfile(recording);
                for (String session : List.of(recording.sessionId(), SPLIT_SESSIONS.get(recording))) {
                    walk.seed(CONNECTOR_DOWNLOAD, Json.createObject().put("sessionRef", sessionRef(session))
                            .put("window", "WHOLE"), "the user's choice of the " + recording + " run");
                }
            }

            assertEquals(new TreeSet<>(REACHED), walk.run());
        }
    }

    /** The {@link ToolSweep} over each run's profile. */
    @Nested
    class Sweep {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everyToolTheProfileAloneCanCallAnswersOrRefusesInASentence(TestappRecording recording) {
            ToolSweep.assertEveryToolAnswersARecording(microscope, profile(recording));
        }
    }

    /** The DOM run as the primary, the direct run as its baseline: the change an agent is asked about. */
    @Nested
    class Compare {

        @Test
        void eachSideCountsWhatItsRunHolds() {
            JsonNode list = microscope.answer("compare_list", pair());

            for (Side side : List.of(
                    new Side(EXECUTION_SAMPLE, DOM.counts().executionSamples(), DIRECT.counts().executionSamples()),
                    new Side(WALL_CLOCK_SAMPLE, DOM.counts().wallClockSamples(), DIRECT.counts().wallClockSamples()),
                    new Side(ALLOCATION_IN_NEW_TLAB, DOM.counts().allocationSamples(), DIRECT.counts().allocationSamples()))) {
                JsonNode type = comparable(list, side.eventType());
                assertEquals(side.primary(), type.path("primarySamples").asInt(), side.eventType());
                assertEquals(side.baseline(), type.path("baselineSamples").asInt(), side.eventType());
            }
            assertEquals(List.of(), strings(list.path("onlyInPrimary")));
            assertEquals(List.of(), strings(list.path("onlyInBaseline")));
        }

        /**
         * The runs differ in length, so the baseline is scaled by the ratio of the two profiles' own
         * durations before a method is weighed against it; both documents state each side's total.
         */
        @Test
        void theMovementsScaleTheBaselineByTheRunsDurations() {
            JsonNode list = microscope.answer("compare_list", pair());
            double ratio = list.path("primary").path("durationMs").asDouble()
                    / list.path("baseline").path("durationMs").asDouble();

            for (String tool : List.of("compare_movements", "compare_flamegraph")) {
                JsonNode answer = microscope.answer(tool, pair().put("eventType", WALL_CLOCK_SAMPLE));
                String document = answer.path("markdown").asString();

                assertFalse(answer.path("truncated").asBoolean(), tool);
                assertEquals(DOM.counts().wallClockSamples(), header(answer, "primary_total"), tool);
                assertEquals(DIRECT.counts().wallClockSamples(), header(answer, "baseline_total"), tool);
                Matcher factor = SCALE_FACTOR.matcher(document);
                assertTrue(factor.find(), () -> tool + " states no baseline_scale_factor");
                assertEquals(String.format(Locale.ROOT, "%.3f", ratio), factor.group(1), tool);
            }
        }

        /**
         * The SQLite driver unpacks its native library to a file named afresh each run; compared, the
         * library is one frame of both runs, never one that a run added or lost.
         */
        @Test
        void aNativeLibraryUnpackedPerRunIsOneFrameOfBoth() {
            for (String eventType : List.of(EXECUTION_SAMPLE, WALL_CLOCK_SAMPLE)) {
                String document = microscope.answer("compare_movements", pair().put("eventType", eventType)
                        .put("limit", 50)).path("markdown").asString();
                List<List<String>> rows = document.lines()
                        .map(line -> List.of(line.split("\\|")).stream().map(String::strip).toList())
                        .filter(cells -> cells.size() > 5 && cells.get(1).endsWith(SQLITE_LIBRARY))
                        .toList();

                assertTrue(rows.stream().noneMatch(cells -> ONE_SIDED.contains(cells.get(5))),
                        () -> eventType + ": " + rows);
            }
        }
    }

    /**
     * The one change between the runs: the DOM run builds every person into a JSON tree before it is
     * written, the direct run writes it straight out. The profiles show it without being told where to
     * look — the services that do it, the tree's builder on the CPU, in the wall clock and among the
     * allocations, the tree's nodes allocated — and only in the DOM run.
     */
    @Nested
    class TheChangeBetweenTheRuns {

        private static final String TREE_BUILDER = "tools.jackson.databind.node.TreeBuildingGenerator";
        private static final String EFFICIENT_SERVICE = "jeffrey.testapp.server.service.EfficientPersonService";
        private static final String INEFFICIENT_SERVICE = "jeffrey.testapp.server.service.InefficientPersonService";
        /** The tree's value nodes, which only a tree allocates. */
        private static final Set<String> TREE_NODES = Set.of(
                "tools.jackson.databind.node.LongNode", "tools.jackson.databind.node.IntNode",
                "tools.jackson.databind.node.StringNode");

        /** Each run's service is its own: the stacks name it, and never the other. */
        @Test
        void eachRunRunsItsOwnService() {
            assertEquals(Set.of(EFFICIENT_SERVICE), services(DIRECT));
            assertEquals(Set.of(INEFFICIENT_SERVICE), services(DOM));
        }

        /** A search for the tree's builder lights the DOM run's graphs only, on every sampler. */
        @Test
        void aSearchForTheTreeBuilderFindsItInTheDomRunOnly() {
            for (String eventType : List.of(EXECUTION_SAMPLE, WALL_CLOCK_SAMPLE, ALLOCATION_IN_NEW_TLAB)) {
                assertTrue(searchMatches(DOM, eventType) > 0, eventType);
                assertEquals(0, searchMatches(DIRECT, eventType), eventType);
            }
        }

        /** By the profile's own events: only the DOM run allocates the tree's nodes. */
        @Test
        void onlyTheDomRunAllocatesTheTreesNodes() {
            assertEquals(TREE_NODES, new TreeSet<>(allocatedTreeNodes(DOM)));
            assertEquals(List.of(), allocatedTreeNodes(DIRECT));
        }

        private Set<String> services(TestappRecording recording) {
            Set<String> services = new TreeSet<>();
            query(recording, "SELECT DISTINCT class_name FROM frames WHERE class_name LIKE 'jeffrey.testapp.server.service.%Service'")
                    .forEach(row -> services.add(row.getFirst()));
            return services;
        }

        private int searchMatches(TestappRecording recording, String eventType) {
            String document = answer("flamegraph_export", recording, Json.createObject()
                    .put("eventType", eventType).put("detail", "SUMMARY").put("search", TREE_BUILDER))
                    .path("markdown").asString();
            Matcher matches = SEARCH_MATCHES.matcher(document);
            assertTrue(matches.find(), () -> "The export states no search_matches: " + document);
            return Integer.parseInt(matches.group(1));
        }

        private List<String> allocatedTreeNodes(TestappRecording recording) {
            String nodes = String.join("', '", TREE_NODES);
            return query(recording, "SELECT DISTINCT weight_entity FROM events WHERE event_type = '"
                    + ALLOCATION_IN_NEW_TLAB + "' AND weight_entity IN ('" + nodes + "')")
                    .stream().map(List::getFirst).toList();
        }
    }

    /** Every count of the run the profile holds: the profiler's samples, and the service's own events. */
    private void assertCounts(TestappRecording recording, String profileId) {
        TestappRecording.Counts counts = recording.counts();
        TestappRecording.Traces traces = recording.traces();
        Map<String, Integer> expected = Map.of(
                EXECUTION_SAMPLE, counts.executionSamples(),
                WALL_CLOCK_SAMPLE, counts.wallClockSamples(),
                ALLOCATION_IN_NEW_TLAB, counts.allocationSamples(),
                THREAD_PARK, counts.threadParks(),
                MONITOR_ENTER, counts.monitorEnters(),
                HTTP_SERVER_EXCHANGE, traces.exchanges(),
                TRACE_SPAN, traces.spans(),
                JDBC_QUERY, traces.queries());
        expected.forEach((eventType, count) -> assertEquals((int) count, samples(profileId, eventType), eventType));
    }

    private ObjectNode pair() {
        return Json.createObject().put("profileId", profile(DOM)).put("baselineProfileId", profile(DIRECT));
    }

    private static JsonNode comparable(JsonNode list, String eventType) {
        for (JsonNode type : list.path("comparable")) {
            if (eventType.equals(type.path("eventType").asString())) {
                return type;
            }
        }
        throw new AssertionError("compare_list cannot compare " + eventType + ": " + list);
    }

    private static int header(JsonNode answer, String name) {
        Matcher matcher = Pattern.compile("(?m)^" + name + ": (\\d+)").matcher(answer.path("markdown").asString());
        assertTrue(matcher.find(), () -> "The document states no " + name);
        return Integer.parseInt(matcher.group(1));
    }

    private record Side(String eventType, int primary, int baseline) {
    }
}
