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
import cafe.jeffrey.test.recording.CheckoutRecording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.CheckoutRecording.BASELINE;
import static cafe.jeffrey.test.recording.CheckoutRecording.CANDIDATE;
import static cafe.jeffrey.test.recording.CheckoutRecording.COUPON;
import static cafe.jeffrey.test.recording.CheckoutRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.CheckoutRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.CheckoutRecording.LINES;
import static cafe.jeffrey.test.recording.CheckoutRecording.LINES_PER_ORDER;
import static cafe.jeffrey.test.recording.CheckoutRecording.MONITOR_ENTER;
import static cafe.jeffrey.test.recording.CheckoutRecording.NUMERIC;
import static cafe.jeffrey.test.recording.CheckoutRecording.OPERATION;
import static cafe.jeffrey.test.recording.CheckoutRecording.OPERATION_KIND;
import static cafe.jeffrey.test.recording.CheckoutRecording.PROMO;
import static cafe.jeffrey.test.recording.CheckoutRecording.STATUS_CODE;
import static cafe.jeffrey.test.recording.CheckoutRecording.STEPS;
import static cafe.jeffrey.test.recording.CheckoutRecording.TRACE_SPAN;
import static cafe.jeffrey.test.recording.CheckoutRecording.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traces, end to end, over the two {@link CheckoutRecording}s: a real recording, through the volume,
 * the parser and the derivation, read back by every trace tool and the UI's trace pages.
 *
 * <p>Every figure is held twice: to what the recording's events say ({@code jfr print}, pinned in
 * {@link CheckoutRecording}), and to what SQL over the profile's own {@code events} finds — the events
 * as the parser stored them, before any derivation touched them — so a figure that drifts says which
 * side moved.
 */
class TracesRecordingsIntegrationTest extends CheckoutVolumeMicroscope {

    /** What a contended monitor enter inside a span becomes: a leaf span of its own. */
    private static final String LOCK_WAIT = "Lock wait";
    private static final String SESSION_TOUCH = "session.touch";
    private static final String PRICE_REPRICE = "price.reprice";
    private static final String COUPON_DISCOUNT = "coupon.discount";
    private static final String SESSION_CACHE = "Checkout$SessionCache";
    private static final String NUMBER_FORMAT_EXCEPTION = "java.lang.NumberFormatException";
    private static final String PROMO_PREFIX = "SPRING-";
    private static final String REGRESSION = "Checkout$PriceCalculator#formatLine";
    private static final String EVENT_FIELD = "EVENT_FIELD";

    /** The contended monitor enters the derivation promotes, one per trace that waited. */
    private static final Map<CheckoutRecording, Integer> LOCK_WAITS = Map.of(BASELINE, 123, CANDIDATE, 158);

    /** HdrHistogram keeps three significant digits: its percentiles are within a thousandth of the exact. */
    private static final double HISTOGRAM_PRECISION = 0.001;

    /** What a span's start is stored to: a span's window, cut there, can miss its own end by under one. */
    private static final long MICROSECOND = 1_000L;

    private static final int PAGE = 100;

    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total: (\\d+)$");
    private static final Pattern BREAKDOWN_LINE = Pattern.compile(
            "(?m)^- (.+?) — self .+, (\\d+) occurrences in (\\d+) traces$");

    /** The recording's own exchanges, by SQL over the stored events: what every trace figure is held to. */
    private static final String EXCHANGES = " FROM events WHERE event_type = '" + HTTP_SERVER_EXCHANGE + "'";
    private static final String TRACE_ID = "json_extract_string(fields, '$.traceId')::BIGINT";
    private static final String COUPON_OF = "json_extract_string(json_extract_string(fields, '$.attributes'), '$."
            + COUPON + "')";

    @Nested
    class Derivation {

        /** One trace per request, its root the exchange, and the four steps under it — nothing else, nothing lost. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyRequestIsOneTraceOfItsFourSteps(CheckoutRecording recording) {
            CheckoutRecording.Counts counts = recording.counts();

            assertEquals(List.of(List.of(String.valueOf(counts.exchanges()), String.valueOf(counts.exchanges()))),
                    query(recording, "SELECT count(*), count(DISTINCT trace_id) FROM traces"));
            Map<String, Integer> byName = spansByName(recording);
            Map<String, Integer> expected = new HashMap<>();
            expected.put(OPERATION, counts.exchanges());
            STEPS.forEach(step -> expected.put(step, counts.exchanges()));
            expected.put(LOCK_WAIT, LOCK_WAITS.get(recording));
            assertEquals(expected, byName);
            assertEquals(counts.spans(), STEPS.stream().mapToInt(byName::get).sum());

            assertEquals(String.valueOf(counts.spans()), scalar(recording,
                    "SELECT count(*) FROM trace_spans s JOIN traces t USING (trace_id)"
                            + " WHERE s.event_type = '" + TRACE_SPAN + "' AND s.parent_span_id = t.root_span_id"));
            assertEquals("0", scalar(recording, "SELECT count(*) FROM trace_spans c WHERE c.parent_span_id IS NOT NULL"
                    + " AND NOT EXISTS (SELECT 1 FROM trace_spans p"
                    + " WHERE p.trace_id = c.trace_id AND p.span_id = c.parent_span_id)"));
        }

        /** The ids the tracer stamped survive as they were: the root of each trace is its exchange, by id. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyTraceIsRootedInItsExchangeByTheIdsTheTracerStamped(CheckoutRecording recording) {
            assertEquals("0", scalar(recording, "SELECT count(*) FROM traces t WHERE NOT EXISTS (SELECT 1" + EXCHANGES
                    + " AND " + TRACE_ID + " = t.trace_id"
                    + " AND json_extract_string(fields, '$.spanId')::BIGINT = t.root_span_id)"));
            assertEquals(Set.of(List.of(OPERATION, OPERATION_KIND, HTTP_SERVER_EXCHANGE)), new HashSet<>(query(recording,
                    "SELECT DISTINCT root_name, root_kind, root_event_type FROM traces")));
        }

        /**
         * A trace lasts exactly as long as its request did, though its last step can appear to end up
         * to a microsecond after it — a span's start is stored to the microsecond.
         */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void aTraceLastsAsLongAsItsRequest(CheckoutRecording recording) {
            List<String> drift = query(recording, "SELECT min(t.duration - x.duration), max(t.duration - x.duration)"
                    + " FROM traces t JOIN (SELECT " + TRACE_ID + " AS trace_id, duration" + EXCHANGES + ") x"
                    + " USING (trace_id)").getFirst();

            assertEquals(List.of("0", "0"), drift);
        }

        /**
         * A contended monitor enter on a span's thread, inside its window, becomes a leaf under the
         * innermost span: every one of them is the session cache's, under {@code session.touch}.
         */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyContendedEnterInsideARequestIsALockWaitUnderTheSessionTouch(CheckoutRecording recording) {
            String inside = scalar(recording, "SELECT count(*) FROM events m WHERE m.event_type = '" + MONITOR_ENTER + "'"
                    + " AND EXISTS (SELECT 1 FROM events x WHERE x.event_type = '" + HTTP_SERVER_EXCHANGE + "'"
                    + " AND x.thread_hash = m.thread_hash AND m.start_timestamp >= x.start_timestamp"
                    + " AND epoch_us(m.start_timestamp) < epoch_us(x.start_timestamp) + x.duration // 1000)");

            assertEquals(String.valueOf(LOCK_WAITS.get(recording)), inside);
            assertEquals(List.of(List.of(SESSION_TOUCH, String.valueOf(LOCK_WAITS.get(recording)))), query(recording,
                    "SELECT p.name, count(*) FROM trace_spans c JOIN trace_spans p"
                            + " ON p.trace_id = c.trace_id AND p.span_id = c.parent_span_id"
                            + " WHERE c.event_type = '" + MONITOR_ENTER + "' GROUP BY p.name"));
        }

        /**
         * Self time is the span's window less its same-thread children's: everything here runs on the
         * request's thread, so the self times of a trace's spans add up to the trace, to the microsecond
         * each span's window is cut at.
         */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void theSelfTimesOfATracesSpansAddUpToTheTrace(CheckoutRecording recording) {
            String offBy = scalar(recording, "SELECT max(abs(t.duration - s.self)) FROM traces t JOIN"
                    + " (SELECT trace_id, sum(self_duration) AS self, count(*) AS spans FROM trace_spans GROUP BY trace_id) s"
                    + " USING (trace_id)");
            long tolerance = MICROSECOND * 6;
            assertTrue(Long.parseLong(offBy) <= tolerance, () -> "A trace's self times miss it by " + offBy + " ns");
        }
    }

    @Nested
    class Latency {

        /** The overview counts what the recording holds, at the durations its exchanges carry. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void theOverviewHoldsTheRecordingsRequests(CheckoutRecording recording) {
            JsonNode overview = answer("traces_overview", recording, Json.createObject()).path("overview");
            List<String> exact = query(recording, "SELECT round(avg(duration))::BIGINT,"
                    + " quantile_cont(duration, 0.95)::BIGINT, quantile_cont(duration, 0.99)::BIGINT, sum(duration)"
                    + EXCHANGES).getFirst();

            assertEquals(recording.counts().exchanges(), overview.path("totalTraces").asInt());
            assertEquals(recording.counts().exchanges() + recording.counts().spans() + LOCK_WAITS.get(recording),
                    overview.path("totalSpans").asInt());
            assertEquals(0, overview.path("errorTraces").asInt());
            assertEquals(0, overview.path("errorSpans").asInt());
            assertEquals(1, overview.path("distinctOperations").asInt());
            assertEquals(recording.traces().slowestNanos(), overview.path("maxNanos").asLong());
            assertEquals(exact, List.of(overview.path("avgNanos").asString(), overview.path("p95Nanos").asString(),
                    overview.path("p99Nanos").asString(), overview.path("totalNanos").asString()));
        }

        /** One operation, the same population: its median is the exchanges' median. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void theOneOperationIsEveryRequest(CheckoutRecording recording) {
            JsonNode operations = answer("traces_operations", recording, Json.createObject());
            JsonNode operation = operations.path("operations").path(0);

            assertEquals(1, operations.path("totalMatching").asInt());
            assertEquals(OPERATION, operation.path("name").asString());
            assertEquals(OPERATION_KIND, operation.path("kind").asString());
            assertEquals(HTTP_SERVER_EXCHANGE, operation.path("eventType").asString());
            assertEquals(recording.counts().exchanges(), operation.path("count").asInt());
            assertEquals(scalar(recording, "SELECT quantile_cont(duration, 0.5)::BIGINT" + EXCHANGES),
                    operation.path("p50Nanos").asString());
        }

        /**
         * The slowest traces come slowest first, the first the longest exchange; its id — a negative long
         * — reads back as sixteen hex digits, through the tools and through the UI.
         */
        @Test
        void theSlowestTraceIsTheLongestExchangeByItsUnsignedHexId() {
            JsonNode traces = answer("traces_slowestTraces", BASELINE, operation().put("limit", 10)).path("traces");
            long longest = Long.parseLong(scalar(BASELINE, "SELECT " + TRACE_ID + EXCHANGES
                    + " ORDER BY duration DESC LIMIT 1"));

            List<Long> durations = new ArrayList<>();
            traces.forEach(trace -> durations.add(trace.path("durationNanos").asLong()));
            assertEquals(durations.stream().sorted((a, b) -> Long.compare(b, a)).toList(), durations);
            assertEquals(BASELINE.traces().slowestNanos(), durations.getFirst());
            assertEquals(hex(longest), traces.path(0).path("traceId").asString());
            MicroscopeClient.assertLinksResolve(traces);

            JsonNode detail = microscope.get(tracesPath(BASELINE) + "/" + hex(longest));
            assertEquals(BASELINE.traces().slowestNanos(), detail.path("trace").path("durationNanos").asLong());
            assertEquals(hex(longest), detail.path("trace").path("traceId").asString());
        }

        /**
         * The HTTP dashboard reads the same exchanges: every request, its status and method, the slowest
         * first. Its percentiles come from a histogram, so they agree with the requests' own to its precision.
         */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void theHttpDashboardReadsTheSameRequests(CheckoutRecording recording) {
            JsonNode http = answer("http_overview", recording, Json.createObject());
            JsonNode header = http.path("header");
            List<String> exact = query(recording, "SELECT quantile_disc(duration, 0.95), quantile_disc(duration, 0.99)"
                    + EXCHANGES).getFirst();
            int requests = recording.counts().exchanges();

            assertEquals(requests, header.path("requestCount").asInt());
            assertEquals(recording.traces().slowestNanos(), header.path("maxResponseTimeNanos").asLong());
            assertEquals(1.0, header.path("successRate").asDouble());
            assertEquals(Json.createArray().add(Json.createObject().put("code", STATUS_CODE).put("count", requests)),
                    http.path("statusCodes"));
            assertEquals(Json.createArray().add(Json.createObject().put("method", "POST").put("count", requests)),
                    http.path("methods"));
            assertEquals(URI, http.path("endpoints").path(0).path("uri").asString());
            assertClose(Long.parseLong(exact.get(0)), header.path("p95ResponseTimeNanos").asLong());
            assertClose(Long.parseLong(exact.get(1)), header.path("p99ResponseTimeNanos").asLong());

            List<Long> slowest = new ArrayList<>();
            http.path("slowRequests").forEach(request -> slowest.add(request.path("responseTimeNanos").asLong()));
            assertEquals(slowest.stream().sorted((a, b) -> Long.compare(b, a)).toList(), slowest);
            assertEquals(recording.traces().slowestNanos(), slowest.getFirst());
        }
    }

    @Nested
    class Attributes {

        /** The root's two attributes, typed by their values, and the exchange's own fields beside them. */
        @Test
        void theKeysAreTheRootsAttributesAndTheExchangesFields() {
            JsonNode keys = answer("traces_attributeKeys", BASELINE, Json.createObject()).path("keys");
            Map<String, JsonNode> byKey = new HashMap<>();
            keys.forEach(key -> byKey.put(key.path("source").asString() + ":" + key.path("owner").asString("")
                    + ":" + key.path("key").asString(), key));
            int requests = BASELINE.counts().exchanges();

            JsonNode coupon = byKey.get("ATTRIBUTE::" + COUPON);
            assertEquals("STRING", coupon.path("valueKind").asString());
            assertEquals(2, coupon.path("distinctValues").asInt());
            assertEquals(requests, coupon.path("traceCount").asInt());
            JsonNode lines = byKey.get("ATTRIBUTE::" + LINES);
            assertEquals("NUMBER", lines.path("valueKind").asString());
            assertEquals(1, lines.path("distinctValues").asInt());
            for (String field : List.of("method", "uri", "statusCode", "mediaType")) {
                JsonNode key = byKey.get(EVENT_FIELD + ":" + HTTP_SERVER_EXCHANGE + ":" + field);
                assertTrue(key != null, () -> field + " is not among " + byKey.keySet());
                assertEquals(requests, key.path("traceCount").asInt(), field);
            }
            assertEquals("NUMBER", byKey.get(EVENT_FIELD + ":" + HTTP_SERVER_EXCHANGE + ":statusCode")
                    .path("valueKind").asString());
            for (String shape : List.of("name", "kind", "status", "eventType")) {
                assertTrue(byKey.containsKey("SPAN_SHAPE::" + shape), () -> shape + " is not among " + byKey.keySet());
            }
        }

        /** Each coupon's traces, counted and timed as SQL over the exchanges' attributes counts and times them. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void eachCouponsTracesAreItsExchanges(CheckoutRecording recording) {
            JsonNode values = answer("traces_attributeValues", recording, Json.createObject().put("key", COUPON));
            Map<String, JsonNode> answered = new HashMap<>();
            values.path("values").forEach(value -> answered.put(value.path("value").asString(), value));
            Map<String, List<String>> exact = new HashMap<>();
            query(recording, "SELECT " + COUPON_OF + ", count(*), sum(duration), quantile_cont(duration, 0.5)::BIGINT,"
                    + " max(duration)" + EXCHANGES + " GROUP BY 1")
                    .forEach(row -> exact.put(row.getFirst(), row.subList(1, row.size())));

            assertEquals(exact.keySet(), answered.keySet());
            exact.forEach((coupon, figures) -> {
                JsonNode value = answered.get(coupon);
                int traces = Integer.parseInt(figures.get(0));
                assertEquals(traces, value.path("traceCount").asInt(), coupon);
                assertEquals(figures.subList(1, 4), List.of(value.path("totalNanos").asString(),
                        value.path("p50Nanos").asString(), value.path("maxNanos").asString()), coupon);
            });
            assertEquals(recording.traces().promo(), answered.get(PROMO).path("traceCount").asInt());
            assertEquals(recording.traces().numeric(), answered.get(NUMERIC).path("traceCount").asInt());
            assertEquals(0, values.path("tracesWithoutKey").asInt());
        }

        /** Paged to the end, a search hands back each promo request once, and only those. */
        @Test
        void aSearchPagedToTheEndFindsEveryPromoRequestOnce() {
            List<String> found = search(BASELINE, Json.createObject().put("key", COUPON).put("value", PROMO));
            Set<String> promo = new TreeSet<>();
            query(BASELINE, "SELECT " + TRACE_ID + EXCHANGES + " AND " + COUPON_OF + " = '" + PROMO + "'")
                    .forEach(row -> promo.add(hex(Long.parseLong(row.getFirst()))));

            assertEquals(BASELINE.traces().promo(), found.size());
            assertEquals(promo, new TreeSet<>(found));
        }

        /** The operators split the population as their names say. */
        @Test
        void theOperatorsSplitThePopulation() {
            int requests = BASELINE.counts().exchanges();
            String fewerLines = String.valueOf(LINES_PER_ORDER - 1);

            assertEquals(BASELINE.traces().numeric(), matching(Json.createObject().put("key", COUPON)
                    .put("operator", "NOT_EQ").put("value", PROMO)));
            assertEquals(requests, matching(Json.createObject().put("key", COUPON).put("operator", "EXISTS")));
            assertEquals(requests, matching(Json.createObject().put("key", LINES)
                    .put("operator", "GT").put("value", fewerLines)));
            assertEquals(requests, matching(Json.createObject().put("key", LINES)
                    .put("operator", "GTE").put("value", String.valueOf(LINES_PER_ORDER))));
            assertEquals(0, matching(Json.createObject().put("key", LINES)
                    .put("operator", "LTE").put("value", fewerLines)));
            assertEquals(BASELINE.traces().promo(), matching(Json.createObject().put("key", COUPON)
                    .put("operator", "CONTAINS").put("value", "rom")));
        }

        private int matching(ObjectNode condition) {
            return answer("traces_attributeSearch", BASELINE, condition.put("limit", 1)).path("totalMatching").asInt();
        }
    }

    /** Why the slow requests were slow: the workload says they waited on the session cache's monitor. */
    @Nested
    class WhereTheTimeWent {

        /** The slowest trace's time is a lock wait under the session touch, on the critical path. */
        @Test
        void theSlowestTraceWaitedOnTheSessionCache() {
            String traceId = answer("traces_slowestTraces", BASELINE, operation().put("limit", 1))
                    .path("traces").path(0).path("traceId").asString();
            String document = answer("traces_traceExport", BASELINE, Json.createObject().put("traceId", traceId))
                    .path("markdown").asString();

            String tree = section(document, "## Span tree");
            assertTrue(tree.contains("- " + SESSION_TOUCH + " [INTERNAL]"), tree);
            assertTrue(Pattern.compile("(?m)^  - " + Pattern.quote(SESSION_TOUCH) + " .*!critical$").matcher(tree).find(), tree);
            assertTrue(Pattern.compile("(?m)^    - " + LOCK_WAIT + " .*!critical$").matcher(tree).find(), tree);
            assertTrue(section(document, "## Where the time went").startsWith("- MONITOR_BLOCKED"), document);
        }

        /** Every lock wait was on the session cache's monitor, the field the monitor enter carried. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyLockWaitWasOnTheSessionCache(CheckoutRecording recording) {
            JsonNode values = answer("traces_attributeValues", recording, Json.createObject().put("key", "monitorClass")
                    .put("source", EVENT_FIELD).put("owner", MONITOR_ENTER)).path("values");

            assertEquals(1, values.size(), values::toString);
            assertTrue(values.path(0).path("value").asString().endsWith(SESSION_CACHE), values::toString);
            assertEquals(LOCK_WAITS.get(recording), values.path(0).path("traceCount").asInt());
        }

        /** The operation's breakdown counts every span by its name, the lock waits among them. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void theOperationsBreakdownCountsEverySpan(CheckoutRecording recording) {
            String document = answer("traces_operationExport", recording, operation()).path("markdown").asString();
            Map<String, List<Integer>> breakdown = new HashMap<>();
            Matcher line = BREAKDOWN_LINE.matcher(section(document, "## Span breakdown"));
            while (line.find()) {
                breakdown.put(line.group(1), List.of(Integer.parseInt(line.group(2)), Integer.parseInt(line.group(3))));
            }
            int requests = recording.counts().exchanges();

            Map<String, List<Integer>> expected = new HashMap<>();
            STEPS.forEach(step -> expected.put(step, List.of(requests, requests)));
            expected.put(LOCK_WAIT, List.of(LOCK_WAITS.get(recording), LOCK_WAITS.get(recording)));
            assertEquals(expected, breakdown);
            assertEquals(LOCK_WAIT, firstBreakdownName(document), "the lock waits rank first by self time");
        }

        /**
         * The coupon parser throws for every promo coupon, inside the discount step: one exception per
         * promo request, charged to that step, none of them escaping it.
         */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyPromoRequestThrewOnceInsideItsDiscountStep(CheckoutRecording recording) {
            assertEquals(List.of(List.of(COUPON_DISCOUNT, NUMBER_FORMAT_EXCEPTION, "false",
                            String.valueOf(recording.traces().promo()), String.valueOf(recording.traces().promo()))),
                    query(recording, "SELECT s.name, x.thrown_class, x.escaped::VARCHAR, count(*),"
                            + " count(DISTINCT x.trace_id) FROM trace_exceptions x JOIN trace_spans s"
                            + " ON s.trace_id = x.trace_id AND s.span_id = x.span_id GROUP BY ALL"));
            assertEquals("0", scalar(recording, "SELECT count(*) FROM trace_exceptions x WHERE x.message NOT LIKE"
                    + " '%\"" + PROMO_PREFIX + "%'"));
            assertEquals("0", scalar(recording, "SELECT count(*) FROM trace_exceptions x JOIN (SELECT " + TRACE_ID
                    + " AS trace_id, " + COUPON_OF + " AS coupon" + EXCHANGES + ") e USING (trace_id)"
                    + " WHERE e.coupon <> '" + PROMO + "'"));
        }

        private static String firstBreakdownName(String document) {
            Matcher line = BREAKDOWN_LINE.matcher(section(document, "## Span breakdown"));
            assertTrue(line.find(), document);
            return line.group(1);
        }
    }

    /** The samples taken while a span, or an operation, was running. */
    @Nested
    class SpanFlamegraphs {

        /**
         * A span's graph holds the CPU samples of its thread inside its window, whole milliseconds;
         * its own time alone holds no more.
         */
        @Test
        void aSpansGraphHoldsItsThreadsSamplesInsideItsWindow() {
            List<String> busiest = query(BASELINE, "SELECT r.trace_id, r.span_id, count(*) AS samples"
                    + " FROM trace_spans r JOIN traces t ON t.trace_id = r.trace_id AND t.root_span_id = r.span_id"
                    + " JOIN events e ON e.event_type = '" + EXECUTION_SAMPLE + "' AND e.thread_hash = r.thread_hash"
                    + inWindow("e", "r") + " GROUP BY ALL ORDER BY samples DESC, r.trace_id LIMIT 1").getFirst();
            String traceId = hex(Long.parseLong(busiest.get(0)));
            String spanId = hex(Long.parseLong(busiest.get(1)));

            int whole = spanSamples(traceId, spanId, false);
            int selfOnly = spanSamples(traceId, spanId, true);

            assertEquals(Integer.parseInt(busiest.get(2)), whole);
            assertTrue(selfOnly <= whole, () -> selfOnly + " of " + whole);
        }

        /** An operation's graph holds the samples of every request's thread inside that request. */
        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void anOperationsGraphHoldsTheSamplesInsideItsRequests(CheckoutRecording recording) {
            String inside = scalar(recording, "SELECT count(*) FROM events e WHERE e.event_type = '" + EXECUTION_SAMPLE + "'"
                    + " AND EXISTS (SELECT 1 FROM trace_spans r JOIN traces t"
                    + " ON t.trace_id = r.trace_id AND t.root_span_id = r.span_id"
                    + " WHERE r.thread_hash = e.thread_hash" + inWindow("e", "r") + ")");

            assertEquals(Integer.parseInt(inside), header(SAMPLES_TOTAL, operationGraph(recording)));
        }

        private int spanSamples(String traceId, String spanId, boolean selfOnly) {
            JsonNode export = answer("traces_spanFlamegraphExport", BASELINE, Json.createObject()
                    .put("traceId", traceId).put("spanId", spanId).put("eventType", EXECUTION_SAMPLE)
                    .put("selfOnly", selfOnly));
            String document = export.path("markdown").asString();
            return document.isEmpty() ? 0 : header(SAMPLES_TOTAL, document);
        }

        /**
         * An event inside a span's window, as the graphs cut it: the millisecond the span started in to
         * the one it ended in, every timestamp floored to its millisecond.
         */
        private static String inWindow(String event, String span) {
            return " AND epoch_us(" + event + ".start_timestamp) // 1000 BETWEEN epoch_us(" + span + ".start_timestamp) // 1000"
                    + " AND (epoch_us(" + span + ".start_timestamp) + " + span + ".duration // 1000) // 1000";
        }
    }

    /** The candidate's regression, found through its traces. */
    @Nested
    class TheRegression {

        /** Only the candidate's requests run the method the regression added. */
        @Test
        void onlyTheCandidatesRequestsRunTheRegression() {
            assertTrue(operationGraph(CANDIDATE).contains(REGRESSION));
            assertFalse(operationGraph(BASELINE).contains(REGRESSION));
        }

        /** Compared with the baseline, unprompted, the method is among the candidate's regressed. */
        @Test
        void theComparisonRanksItAmongTheRegressed() {
            String document = microscope.answer("compare_movements", Json.createObject()
                    .put("profileId", profile(CANDIDATE)).put("baselineProfileId", profile(BASELINE))
                    .put("eventType", EXECUTION_SAMPLE)).path("markdown").asString();

            assertTrue(section(document, "## Regressed").contains(REGRESSION), document);
        }

        /** The step it sits in takes longer on its own. */
        @Test
        void theRepricingStepTakesLongerOnItsOwn() {
            long baseline = medianSelf(BASELINE, PRICE_REPRICE);
            long candidate = medianSelf(CANDIDATE, PRICE_REPRICE);

            assertTrue(candidate > baseline * 5 / 4, () -> candidate + " against " + baseline);
        }

        private long medianSelf(CheckoutRecording recording, String step) {
            return Long.parseLong(scalar(recording, "SELECT quantile_cont(self_duration, 0.5)::BIGINT FROM trace_spans"
                    + " WHERE name = '" + step + "'"));
        }
    }

    /**
     * The same run analysed a second time — imported from a copy of its file, a recording and a profile
     * of its own — derives the same traces, row for row: every id, the ones the derivation mints for a
     * promoted lock wait or a charged throw included, every window and self time, the exceptions and the
     * attribute index, whatever the parse's threads and the clustering made of the rows' order.
     */
    @Nested
    class AnalysedAgain {

        private static final List<String> DERIVED = List.of(
                "traces", "trace_spans", "trace_exceptions", "trace_span_attributes", "trace_attribute_values",
                "trace_attribute_keys", "trace_span_payloads");

        @Test
        void theSameRunDerivesTheSameTraces(@TempDir Path dir) throws IOException {
            Path file = Files.write(dir.resolve(BASELINE.fileName()), BASELINE.bytes());
            JsonNode analyzed = microscope.answer("recordings_analyzeFile",
                    Json.createObject().put("path", file.toString()));
            assertEquals("READY", analyzed.path("status").asString(), analyzed::toString);
            String again = analyzed.path("profileId").asString();

            assertFalse(again.equals(profile(BASELINE)), "a second profile of the same run");
            for (String table : DERIVED) {
                String digest = "SELECT count(*), md5(string_agg(CAST(t AS VARCHAR), '|' ORDER BY CAST(t AS VARCHAR)))"
                        + " FROM " + table + " t";
                assertEquals(query(BASELINE, digest), query(again, digest), table);
            }
        }
    }

    /** The UI's trace pages answer from the same derivation as the tools. */
    @Nested
    class TheUiPages {

        @Test
        void theUiReadsTheSameTracesAsTheTools() {
            JsonNode overview = answer("traces_overview", BASELINE, Json.createObject()).path("overview");
            JsonNode page = microscope.get(tracesPath(BASELINE) + "/overview");

            assertEquals(overview.path("totalTraces").asLong(), page.path("totalTraces").asLong(), page::toString);
            assertEquals(overview.path("totalSpans").asLong(), page.path("totalSpans").asLong(), page::toString);

            String slowest = answer("traces_slowestTraces", BASELINE, operation().put("limit", 1))
                    .path("traces").path(0).path("traceId").asString();
            JsonNode rows = microscope.get(tracesPath(BASELINE) + "/operation/slowest-traces?" + operationQuery() + "&limit=1");
            assertEquals(slowest, rows.path(0).path("traceId").asString(), rows::toString);

            JsonNode panels = microscope.get(tracesPath(BASELINE) + "/operation/panels?" + operationQuery());
            int graphed = header(SAMPLES_TOTAL, operationGraph(BASELINE));
            List<String> answered = new ArrayList<>();
            panels.forEach(panel -> answered.add(panel.toString()));
            assertTrue(answered.stream().anyMatch(panel -> panel.contains(EXECUTION_SAMPLE)
                    && panel.contains(String.valueOf(graphed))), answered::toString);
        }

        private static String operationQuery() {
            return "name=" + OPERATION.replace(" ", "%20").replace("/", "%2F") + "&kind=" + OPERATION_KIND
                    + "&eventType=" + HTTP_SERVER_EXCHANGE;
        }
    }

    /**
     * The {@link FollowUpWalk} over the traced runs: from {@code connector_status} and the user's two
     * downloads, every call an answer suggests answers in its turn. The tools it reaches are pinned —
     * the trace tools among them, reached only from what the answers name.
     */
    @Nested
    class Walk {

        /**
         * What it leaves out is what the runs do not hold (notifications, JDBC, gRPC, a heap dump). The
         * attribute tools are reached from the traces' overview: keys, then a key's values, then a search.
         */
        private static final Set<String> REACHED = Set.of(
                "connector_status", "connector_sessions", "connector_download", "profiles_summary",
                "profiles_features", "flamegraph_list", "flamegraph_export", "memory_allocations",
                "memory_leakCandidates", "blocking_overview", "blocking_monitors", "timeline_hotWindows",
                "timeline_zoom", "io_overview", "http_overview", "http_endpoint", "traces_overview",
                "traces_attributeKeys", "traces_attributeValues", "traces_attributeSearch",
                "traces_operations", "traces_operationExport", "traces_operationFlamegraphExport",
                "traces_slowestTraces", "traces_traceExport", "traces_spanFlamegraphExport",
                "jfr_listEventTypes", "jfr_describeEventType", "jfr_queryEvents", "jvm_autoAnalysis",
                "jvm_system", "jvm_configuration", "jvm_flags", "jvm_container", "jvm_gc", "jvm_gcDetail",
                "jvm_jit", "jvm_threads", "jvm_nativeMemory", "jvm_safepoints", "jvm_exceptions",
                "jvm_exceptionSites");

        /** Both runs are analysed first, so a download suggests its profile whatever ran before. */
        @Test
        void everyCallAnAnswerSuggestsAnswersInItsTurn() {
            FollowUpWalk walk = new FollowUpWalk(microscope).seed(CONNECTOR_STATUS, Json.createObject(), "the walk's start");
            for (CheckoutRecording recording : CheckoutRecording.values()) {
                profile(recording);
                walk.seed(CONNECTOR_DOWNLOAD, Json.createObject().put("sessionRef", sessionRef(SESSIONS.get(recording)))
                        .put("window", "WHOLE"), "the user's choice of the " + recording + " run");
            }

            assertEquals(new TreeSet<>(REACHED), walk.run());
        }
    }

    /** The {@link ToolSweep} over each run's profile: a profile with traces fails no tool inside. */
    @Nested
    class Sweep {

        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void everyToolTheProfileAloneCanCallAnswersOrRefusesInASentence(CheckoutRecording recording) {
            ToolSweep.assertEveryToolAnswersARecording(microscope, profile(recording));
        }
    }

    /** Every trace of a search, paged through its cursor. */
    private List<String> search(CheckoutRecording recording, ObjectNode condition) {
        List<String> found = new ArrayList<>();
        String cursor = null;
        do {
            ObjectNode page = condition.deepCopy().put("limit", PAGE);
            if (cursor != null) {
                page.put("cursor", cursor);
            }
            JsonNode answer = answer("traces_attributeSearch", recording, page);
            answer.path("matches").forEach(match -> found.add(match.path("trace").path("traceId").asString()));
            cursor = answer.path("hasMore").asBoolean() ? answer.path("nextCursor").asString() : null;
        } while (cursor != null);
        return found;
    }

    private String operationGraph(CheckoutRecording recording) {
        return answer("traces_operationFlamegraphExport", recording, operation().put("graphEventType", EXECUTION_SAMPLE))
                .path("markdown").asString();
    }

    private Map<String, Integer> spansByName(CheckoutRecording recording) {
        Map<String, Integer> byName = new HashMap<>();
        query(recording, "SELECT name, count(*) FROM trace_spans GROUP BY name")
                .forEach(row -> byName.put(row.get(0), Integer.parseInt(row.get(1))));
        return byName;
    }

    private String tracesPath(CheckoutRecording recording) {
        return "/api/internal/profiles/" + profile(recording) + "/traces";
    }

    /** The section of a Markdown document under a heading, up to the next heading of its level. */
    private static String section(String document, String heading) {
        int start = document.indexOf(heading);
        assertTrue(start >= 0, () -> "No " + heading + " in " + document);
        int body = start + heading.length();
        int end = document.indexOf("\n## ", body);
        return document.substring(body, end < 0 ? document.length() : end).strip();
    }

    private static int header(Pattern pattern, String document) {
        Matcher matcher = pattern.matcher(document);
        assertTrue(matcher.find(), () -> "The document states no " + pattern.pattern());
        return Integer.parseInt(matcher.group(1));
    }

    private static void assertClose(long exact, long histogram) {
        assertTrue(Math.abs(exact - histogram) <= exact * HISTOGRAM_PRECISION, () -> histogram + " against " + exact);
    }
}
