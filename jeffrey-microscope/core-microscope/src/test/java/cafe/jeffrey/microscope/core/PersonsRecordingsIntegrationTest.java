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
import cafe.jeffrey.test.recording.PersonsRecording;
import cafe.jeffrey.test.recording.PersonsRecording.ProfilerChunk;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.PersonsRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.PersonsRecording.DIRECT;
import static cafe.jeffrey.test.recording.PersonsRecording.DOM;
import static cafe.jeffrey.test.recording.PersonsRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.PersonsRecording.MONITOR_ENTER;
import static cafe.jeffrey.test.recording.PersonsRecording.THREAD_PARK;
import static cafe.jeffrey.test.recording.PersonsRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microscope over realistic recordings: a service under load, profiled for CPU, wall clock,
 * allocation and locks, with the JDK's own events beside them — near a million samples a run. What
 * the small session cannot show, these do: wall-clock samples folded into events, I/O, exceptions,
 * GC detail and safepoints, and two runs of one service to compare.
 */
class PersonsRecordingsIntegrationTest extends PersonsVolumeMicroscope {

    /** Every count each recording's profile reports is the recording's own. */
    @Nested
    class Figures {

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        void theProfileCountsWhatTheRecordingHolds(PersonsRecording recording) {
            String profileId = profile(recording);

            assertEquals(recording.counts().executionSamples(), samples(profileId, EXECUTION_SAMPLE));
            assertEquals(recording.counts().wallClockSamples(), samples(profileId, WALL_CLOCK_SAMPLE));
            assertEquals(recording.counts().allocationSamples(), samples(profileId, ALLOCATION_IN_NEW_TLAB));
            assertEquals(recording.counts().threadParks(), samples(profileId, THREAD_PARK));
            assertEquals(recording.counts().monitorEnters(), samples(profileId, MONITOR_ENTER));
        }

        /**
         * async-profiler folds an idle thread's wall-clock samples into one event: SQL over the
         * events counts fewer rows than samples, and the samples are what every dashboard reports.
         */
        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        void wallClockEventsCarryMoreSamplesThanThereAreEvents(PersonsRecording recording) {
            JsonNode result = microscope.answer("jfr_executeQuery", Json.createObject()
                    .put("profileId", profile(recording))
                    .put("query", "SELECT count(*), sum(samples) FROM events WHERE event_type = '"
                            + WALL_CLOCK_SAMPLE + "'"));

            List<String> row = strings(result.path("rows").get(0));
            long events = Long.parseLong(row.get(0));
            assertEquals(recording.counts().wallClockSamples(), Long.parseLong(row.get(1)));
            assertTrue(events < recording.counts().wallClockSamples(), () -> events + " events");
        }

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        void theCpuFlamegraphTotalsEveryCpuSample(PersonsRecording recording) {
            JsonNode export = microscope.answer("flamegraph_export", Json.createObject()
                    .put("profileId", profile(recording)).put("eventType", EXECUTION_SAMPLE).put("detail", "SUMMARY"));

            assertEquals(recording.counts().executionSamples(), header(export, "samples_total"));
        }
    }

    /**
     * The {@link FollowUpWalk} over both runs, as one file and as chunks: every call an answer
     * suggests answers in its turn. The tools it reaches are pinned, and against the small session
     * they add what this load holds — I/O, exceptions, GC detail, safepoints, leak candidates and the
     * traced operations' status; a chunked run, holding the profiler's events without the JDK's, is
     * pointed at {@code jvm_sections}.
     */
    @Nested
    class Walk {

        private static final Set<String> REACHED = Set.of(
                "connector_status", "connector_sessions", "connector_download", "profiles_summary",
                "profiles_features", "flamegraph_list", "flamegraph_export", "memory_allocations",
                "memory_leakCandidates", "blocking_overview", "blocking_monitors", "timeline_hotWindows",
                "timeline_zoom", "io_overview", "io_endpoints", "io_slowest", "traces_operations",
                "jfr_listEventTypes", "jfr_describeEventType", "jfr_queryEvents", "jvm_autoAnalysis",
                "jvm_sections", "jvm_system", "jvm_configuration", "jvm_flags", "jvm_container", "jvm_gc",
                "jvm_gcDetail", "jvm_jit", "jvm_threads", "jvm_nativeMemory", "jvm_safepoints", "jvm_exceptions",
                "jvm_exceptionSites");

        /**
         * Every session is analysed first, as the other tests here leave them in whatever order they
         * run: a download of a session already analysed suggests its profile rather than another
         * analysis, and a listing of sessions all here suggests none of their files, so the walk
         * reaches the same tools every time.
         */
        @Test
        void everyCallAnAnswerSuggestsAnswersInItsTurn() {
            FollowUpWalk walk = new FollowUpWalk(microscope).seed(CONNECTOR_STATUS, Json.createObject(), "the walk's start");
            for (PersonsRecording recording : PersonsRecording.values()) {
                profile(recording);
                chunkedProfile(recording);
                for (String session : List.of(SESSIONS.get(recording), CHUNKED_SESSIONS.get(recording))) {
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
        @EnumSource(PersonsRecording.class)
        void everyToolTheProfileAloneCanCallAnswersOrRefusesInASentence(PersonsRecording recording) {
            ToolSweep.assertEveryToolAnswersARecording(microscope, profile(recording));
        }
    }

    /**
     * The same runs as a profiler writing a file per chunk leaves them: three chunks a session, the
     * older ones compressed by the hub. The connector picks chunks by when they began, and what it
     * brings holds exactly the chunks' samples.
     */
    @Nested
    class ChunkedSessions {

        private static final String JFR_EXTENSION = ".jfr";
        private static final String ARCHIVE_SUFFIX = ".lz4";

        /** async-profiler's chunks hold all of its events, so the session reads as the file it was cut from. */
        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        void theChunksAddUpToTheFileTheyWereCutFrom(PersonsRecording recording) {
            String profileId = chunkedProfile(recording);

            assertEquals(recording.counts().executionSamples(), samples(profileId, EXECUTION_SAMPLE));
            assertEquals(recording.counts().wallClockSamples(), samples(profileId, WALL_CLOCK_SAMPLE));
            assertEquals(recording.counts().allocationSamples(), samples(profileId, ALLOCATION_IN_NEW_TLAB));
            assertEquals(recording.counts().threadParks(), samples(profileId, THREAD_PARK));
            assertEquals(recording.counts().monitorEnters(), samples(profileId, MONITOR_ENTER));
        }

        /** The hub compressed every chunk but the newest of the pod's newest session. */
        @Test
        void theSessionsListTheirChunksAsTheHubLeftThem() {
            assertEquals(List.of("JFR_LZ4", "JFR_LZ4", "JFR_LZ4"), recordingTypes(DIRECT));
            assertEquals(List.of("JFR", "JFR_LZ4", "JFR_LZ4"), recordingTypes(DOM));
        }

        /**
         * The session's data ends one chunk length after its newest chunk began, the length the
         * spacing of the chunks' names measures — the lower of 4m32s and 5m25s. The last five minutes
         * before it reach back into the middle chunk, which runs until the newest one begins.
         */
        @Test
        void theLastFiveMinutesAreTheTwoChunksTheyTouch() {
            List<ProfilerChunk> chunks = DOM.profilerChunks();
            Duration chunkLength = Duration.between(DOM.openedAt(chunks.get(0)), DOM.openedAt(chunks.get(1)));
            Instant dataEnd = DOM.openedAt(chunks.getLast()).plus(chunkLength);
            assertTrue(dataEnd.minus(Duration.ofMinutes(5)).isBefore(DOM.openedAt(chunks.getLast())));

            JsonNode download = download(chunkedRef(DOM), Json.createObject().put("window", "LAST_MINUTES").put("minutes", 5));

            assertEquals(2, download.path("recordingFiles").asInt());
            assertEquals(dataEnd.toEpochMilli(), download.path("endEpochMs").asLong());
            assertEquals(chunks.get(1).counts().executionSamples() + chunks.get(2).counts().executionSamples(),
                    samples(profileOf(download), EXECUTION_SAMPLE));
        }

        /** The peak compares compressed sizes only: the newest chunk, not compressed yet, is not among them. */
        @Test
        void thePeakIsTheLargestArchive() throws IOException {
            ProfilerChunk largest = null;
            long largestSize = -1;
            for (ProfilerChunk chunk : DOM.profilerChunks().subList(0, 2)) {
                long size = Files.size(CHUNKED_SESSION_DIRS.get(DOM).resolve(archiveName(DOM, chunk)));
                if (size > largestSize) {
                    largest = chunk;
                    largestSize = size;
                }
            }

            JsonNode download = download(chunkedRef(DOM), Json.createObject().put("window", "PEAK"));

            assertEquals(List.of(fileId(DOM, largest)), strings(download.path("fileIds")));
            assertEquals(largest.counts().executionSamples(), samples(profileOf(download), EXECUTION_SAMPLE));
        }

        /** A minute inside the middle chunk is that chunk alone, and the profile holds only its samples. */
        @Test
        void aWindowInsideOneChunkTakesOnlyThatChunk() {
            ProfilerChunk middle = DIRECT.profilerChunks().get(1);
            Instant start = DIRECT.openedAt(middle).plus(Duration.ofMinutes(1));

            JsonNode download = download(chunkedRef(DIRECT), Json.createObject().put("window", "CUSTOM")
                    .put("startEpochMs", start.toEpochMilli())
                    .put("endEpochMs", start.plus(Duration.ofMinutes(1)).toEpochMilli()));

            assertEquals(1, download.path("recordingFiles").asInt());
            String profileId = profileOf(download);
            assertEquals(middle.counts().executionSamples(), samples(profileId, EXECUTION_SAMPLE));
            assertEquals(middle.counts().wallClockSamples(), samples(profileId, WALL_CLOCK_SAMPLE));
        }

        /** The first and the last chunk without the middle one would be one recording with a silent hole. */
        @Test
        void aPickThatSkipsTheMiddleChunkIsRefusedNamingIt() {
            List<ProfilerChunk> chunks = DIRECT.profilerChunks();
            ObjectNode arguments = Json.createObject().put("sessionRef", chunkedRef(DIRECT));
            arguments.putArray("fileIds").add(fileId(DIRECT, chunks.getFirst())).add(fileId(DIRECT, chunks.getLast()));

            String refusal = microscope.refusal(CONNECTOR_DOWNLOAD, arguments);

            assertTrue(refusal.contains(archiveName(DIRECT, chunks.get(1))), refusal);
        }

        private List<String> recordingTypes(PersonsRecording recording) {
            JsonNode files = microscope.answer("connector_files", Json.createObject().put("sessionRef", chunkedRef(recording)));
            List<String> types = new ArrayList<>();
            for (JsonNode file : files.path("files")) {
                if ("RECORDING".equals(file.path("category").asString())) {
                    types.add(file.path("type").asString());
                }
            }
            return types;
        }

        private String chunkedRef(PersonsRecording recording) {
            return sessionRef(CHUNKED_SESSIONS.get(recording));
        }

        private static String fileId(PersonsRecording recording, ProfilerChunk chunk) {
            String name = VolumeWriter.recordingName(recording.openedAt(chunk), ZoneOffset.UTC);
            return name.substring(0, name.length() - JFR_EXTENSION.length());
        }

        private static String archiveName(PersonsRecording recording, ProfilerChunk chunk) {
            return VolumeWriter.recordingName(recording.openedAt(chunk), ZoneOffset.UTC) + ARCHIVE_SUFFIX;
        }
    }

    /** The DOM run as the primary, the direct run as its baseline: the change an agent is asked about. */
    @Nested
    class Compare {

        private static final Pattern SCALE_FACTOR = Pattern.compile("(?m)^baseline_scale_factor: ([0-9.]+)$");

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
        void theMovementsScaleTheShorterBaselineByTheRunsDurations() {
            JsonNode list = microscope.answer("compare_list", pair());
            double ratio = list.path("primary").path("durationMs").asDouble()
                    / list.path("baseline").path("durationMs").asDouble();

            for (String tool : List.of("compare_movements", "compare_flamegraph")) {
                JsonNode answer = microscope.answer(tool, pair().put("eventType", EXECUTION_SAMPLE));
                String document = answer.path("markdown").asString();

                assertFalse(answer.path("truncated").asBoolean(), tool);
                assertEquals(DOM.counts().executionSamples(), header(answer, "primary_total"), tool);
                assertEquals(DIRECT.counts().executionSamples(), header(answer, "baseline_total"), tool);
                Matcher factor = SCALE_FACTOR.matcher(document);
                assertTrue(factor.find(), () -> tool + " states no baseline_scale_factor");
                assertEquals(String.format(Locale.ROOT, "%.3f", ratio), factor.group(1), tool);
            }
        }

        private ObjectNode pair() {
            return Json.createObject().put("profileId", profile(DOM)).put("baselineProfileId", profile(DIRECT));
        }

        private JsonNode comparable(JsonNode list, String eventType) {
            for (JsonNode type : list.path("comparable")) {
                if (eventType.equals(type.path("eventType").asString())) {
                    return type;
                }
            }
            throw new AssertionError("compare_list cannot compare " + eventType + ": " + list);
        }

        private record Side(String eventType, int primary, int baseline) {
        }
    }

    /** A number in the header of an exported document, such as {@code samples_total: 39668}. */
    private static int header(JsonNode export, String key) {
        Matcher matcher = Pattern.compile("(?m)^" + key + ": (\\d+)").matcher(export.path("markdown").asString());
        assertTrue(matcher.find(), () -> "The document states no " + key);
        return Integer.parseInt(matcher.group(1));
    }
}
