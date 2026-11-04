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

import cafe.jeffrey.microscope.model.StacktraceTag;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.DOM;
import static cafe.jeffrey.test.recording.TestappRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.TestappRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wall clock, end to end, over the two {@link TestappRecording}s. async-profiler samples every
 * thread every 10 ms whether it runs or waits, and folds an idle thread's samples into one event,
 * counting them in its {@code samples} field — so every answer about the wall clock counts samples,
 * never events, and the two differ several times over.
 *
 * <p>The samples are in async-profiler's chain and the requests in the JDK's; a request's wall clock
 * is the samples taken on its thread while it ran, and the two chains name that thread alike.
 */
class TestappWallClockIntegrationTest extends TestappVolumeMicroscope {

    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total: (\\d+)$");
    private static final Pattern PRIMARY_TOTAL = Pattern.compile("(?m)^primary_total: (\\d+)$");
    private static final Pattern BASELINE_TOTAL = Pattern.compile("(?m)^baseline_total: (\\d+)");

    /** The request threads, as both chains name them. */
    private static final String REQUEST_THREADS = "http-nio-%-exec-%";
    private static final int REQUEST_THREAD_COUNT = 10;

    /** The wall-clock events taken on the request threads, as {@code jfr} reads async-profiler's chunk. */
    private static final Map<TestappRecording, Integer> REQUEST_THREAD_EVENTS = Map.of(DIRECT, 10_153, DOM, 11_172);

    private static final String OPERATION = "GET /persons";
    private static final int ZOOM_BUCKET_MS = 100;

    @Nested
    class SamplesNotEvents {

        /** The graph counts every sample, aggregated or split per thread. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theGraphCountsEverySample(TestappRecording recording) {
            int samples = recording.counts().wallClockSamples();

            assertEquals(samples, samplesTotal(export(recording, Json.createObject())));
            assertEquals(samples, samplesTotal(export(recording, Json.createObject().put("threadMode", true))));
            assertTrue(recording.counts().wallClockEvents() * 2 < samples, "the samples are several times the events");
        }

        /**
         * Leaving out the idle stacks — a thread parked, polling or waiting on a socket — keeps exactly
         * the samples of the stacks the parse did not tag idle, and the comparison keeps the same.
         */
        @Test
        void leavingOutTheIdleKeepsTheSamplesNotTaggedIdle() {
            int dom = samplesTotal(export(DOM, Json.createObject().put("excludeIdle", true)));
            int direct = samplesTotal(export(DIRECT, Json.createObject().put("excludeIdle", true)));
            String comparison = microscope.answer("compare_movements", Json.createObject()
                    .put("profileId", profile(DOM)).put("baselineProfileId", profile(DIRECT))
                    .put("eventType", WALL_CLOCK_SAMPLE).put("excludeIdle", true)).path("markdown").asString();

            assertEquals(notIdle(DOM), dom);
            assertEquals(notIdle(DIRECT), direct);
            assertEquals(dom, header(PRIMARY_TOTAL, comparison));
            assertEquals(direct, header(BASELINE_TOTAL, comparison));
            assertTrue(dom < DOM.counts().wallClockSamples(), () -> dom + " of " + DOM.counts().wallClockSamples());
        }

        private int notIdle(TestappRecording recording) {
            return Integer.parseInt(scalar(recording, "SELECT sum(e.samples) FROM events e JOIN stacktraces s"
                    + " USING (stacktrace_hash) WHERE e.event_type = '" + WALL_CLOCK_SAMPLE + "'"
                    + " AND NOT list_contains(s.tag_ids, " + StacktraceTag.EXCLUDE_IDLE.id() + ")"));
        }
    }

    /** When the samples landed, second by second and inside a second. */
    @Nested
    class Timeline {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theTimelineTotalsEverySample(TestappRecording recording) {
            JsonNode timeline = hotWindows(recording);

            assertEquals(recording.counts().wallClockSamples(), timeline.path("total").asInt());
        }

        /** Zoomed into, the hottest second holds what it was ranked by, and what SQL finds in it. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theHottestSecondZoomedIntoHoldsItsSamples(TestappRecording recording) {
            JsonNode hottest = hotWindows(recording).path("hottestWindows").path(0);
            long start = hottest.path("startEpochMs").asLong();
            long end = hottest.path("endEpochMs").asLong();

            JsonNode zoom = answer("timeline_zoom", recording, Json.createObject().put("eventType", WALL_CLOCK_SAMPLE)
                    .put("startEpochMs", start).put("endEpochMs", end).put("bucketMs", ZOOM_BUCKET_MS));

            assertEquals(hottest.path("value").asLong(), zoom.path("total").asLong(), zoom::toString);
            assertEquals(scalar(recording, "SELECT sum(samples) FROM events WHERE event_type = '" + WALL_CLOCK_SAMPLE + "'"
                    + " AND epoch_us(start_timestamp) // 1000 >= " + start
                    + " AND epoch_us(start_timestamp) // 1000 < " + end), zoom.path("total").asString());
        }

        private JsonNode hotWindows(TestappRecording recording) {
            return answer("timeline_hotWindows", recording, Json.createObject().put("eventType", WALL_CLOCK_SAMPLE));
        }
    }

    /** One thread is one thread, whichever chain named it. */
    @Nested
    class Threads {

        /**
         * Each request thread is one row, and the samples the profiler took on it land there: the two
         * chains describe it alike, so it is not split in two.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void eachRequestThreadIsOneThreadInBothChains(TestappRecording recording) {
            assertEquals(List.of(List.of(String.valueOf(REQUEST_THREAD_COUNT), String.valueOf(REQUEST_THREAD_COUNT))),
                    query(recording, "SELECT count(*), count(DISTINCT name) FROM threads WHERE name LIKE '"
                            + REQUEST_THREADS + "'"));
            assertEquals(String.valueOf(REQUEST_THREAD_EVENTS.get(recording)), scalar(recording,
                    "SELECT count(*) FROM events e JOIN threads t USING (thread_hash) WHERE e.event_type = '"
                            + WALL_CLOCK_SAMPLE + "' AND t.name LIKE '" + REQUEST_THREADS + "'"));
            assertEquals(String.valueOf(recording.traces().exchanges()), scalar(recording,
                    "SELECT count(*) FROM events e JOIN threads t USING (thread_hash) WHERE e.event_type = '"
                            + HTTP_SERVER_EXCHANGE + "' AND t.name LIKE '" + REQUEST_THREADS + "'"));
        }
    }

    /** A request's wall clock: the samples its thread took while it ran, across the two chains. */
    @Nested
    class InsideRequests {

        /** A request's graph holds its thread's samples inside its window — samples, not events. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void aRequestsGraphHoldsItsThreadsSamples(TestappRecording recording) {
            List<String> busiest = query(recording, "SELECT r.trace_id, r.span_id, sum(e.samples) AS samples"
                    + " FROM trace_spans r JOIN traces t ON t.trace_id = r.trace_id AND t.root_span_id = r.span_id"
                    + " JOIN events e ON e.event_type = '" + WALL_CLOCK_SAMPLE + "' AND e.thread_hash = r.thread_hash"
                    + inWindow("e", "r") + " GROUP BY ALL ORDER BY samples DESC, r.trace_id LIMIT 1").getFirst();

            JsonNode export = answer("traces_spanFlamegraphExport", recording, Json.createObject()
                    .put("traceId", hex(Long.parseLong(busiest.get(0)))).put("spanId", hex(Long.parseLong(busiest.get(1))))
                    .put("eventType", WALL_CLOCK_SAMPLE));

            assertEquals(Integer.parseInt(busiest.get(2)), samplesTotal(export.path("markdown").asString()));
        }

        /** An operation's graph holds the samples of every one of its requests' threads inside it. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void anOperationsGraphHoldsTheSamplesInsideItsRequests(TestappRecording recording) {
            String inside = scalar(recording, "SELECT sum(e.samples) FROM events e WHERE e.event_type = '" + WALL_CLOCK_SAMPLE + "'"
                    + " AND EXISTS (SELECT 1 FROM trace_spans r JOIN traces t"
                    + " ON t.trace_id = r.trace_id AND t.root_span_id = r.span_id"
                    + " WHERE t.root_name = '" + OPERATION + "' AND r.thread_hash = e.thread_hash" + inWindow("e", "r") + ")");

            JsonNode export = answer("traces_operationFlamegraphExport", recording, Json.createObject()
                    .put("name", OPERATION).put("kind", "SERVER").put("eventType", HTTP_SERVER_EXCHANGE)
                    .put("graphEventType", WALL_CLOCK_SAMPLE));

            assertEquals(Integer.parseInt(inside), samplesTotal(export.path("markdown").asString()));
        }

        /** An event inside a span's window, as the graphs cut it: whole milliseconds, every timestamp floored. */
        private static String inWindow(String event, String span) {
            return " AND epoch_us(" + event + ".start_timestamp) // 1000 BETWEEN epoch_us(" + span + ".start_timestamp) // 1000"
                    + " AND (epoch_us(" + span + ".start_timestamp) + " + span + ".duration // 1000) // 1000";
        }
    }

    private String export(TestappRecording recording, ObjectNode options) {
        return answer("flamegraph_export", recording, options.put("eventType", WALL_CLOCK_SAMPLE).put("detail", "SUMMARY"))
                .path("markdown").asString();
    }

    private static int samplesTotal(String document) {
        return header(SAMPLES_TOTAL, document);
    }

    private static int header(Pattern pattern, String document) {
        Matcher matcher = pattern.matcher(document);
        assertTrue(matcher.find(), () -> "The document states no " + pattern.pattern());
        return Integer.parseInt(matcher.group(1));
    }
}
