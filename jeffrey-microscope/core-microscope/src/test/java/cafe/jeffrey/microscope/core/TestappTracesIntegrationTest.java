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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.TestappRecording.CONNECTION_ACQUIRED;
import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.DOM;
import static cafe.jeffrey.test.recording.TestappRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.TestappRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_DELETE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_QUERY;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_UPDATE;
import static cafe.jeffrey.test.recording.TestappRecording.MODE;
import static cafe.jeffrey.test.recording.TestappRecording.STARTUP_QUERY;
import static cafe.jeffrey.test.recording.TestappRecording.TO_JSON_TREE;
import static cafe.jeffrey.test.recording.TestappRecording.TRACE_SPAN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traces, end to end, over the two {@link TestappRecording}s: a service whose every request is a trace
 * — an HTTP exchange as its root, the service's steps and its JDBC statements under it — recorded in
 * the JDK's chain while async-profiler sampled in its own.
 *
 * <p>Every figure is held to the recording's own events, as {@code jfr} reads them and pinned in
 * {@link TestappRecording}, and to SQL over the profile's stored {@code events} — the events before any
 * derivation touched them — so a figure that drifts says which side moved.
 */
class TestappTracesIntegrationTest extends TestappVolumeMicroscope {

    private static final String SERVER = "SERVER";
    private static final String CLIENT = "CLIENT";
    private static final String INTERNAL = "INTERNAL";
    private static final String EVENT_FIELD = "EVENT_FIELD";
    private static final String GET_PERSONS = "GET /persons";
    private static final String PERSONS_BY_COUNT = "/persons/{count}";
    private static final String POOL = "testapp-sqlite";

    /** The JDK's blocking events the derivation promotes into leaf spans of the requests they happened in. */
    private static final Map<TestappRecording, Integer> PROMOTED = Map.of(DIRECT, 19, DOM, 54);

    private static final int PAGE = 100;

    /** The length from which the parse stores an event's longest text apart from the event. */
    private static final int POOLED_LENGTH = 64;

    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total: (\\d+)$");
    private static final Pattern BREAKDOWN_NAME = Pattern.compile("(?m)^- (.+?) — self ");

    private static final String STATEMENTS = "'" + JDBC_QUERY + "', '" + JDBC_UPDATE + "', '" + JDBC_DELETE + "'";
    private static final String EXCHANGES = " FROM events WHERE event_type = '" + HTTP_SERVER_EXCHANGE + "'";

    @Nested
    class Derivation {

        /** One trace per request, rooted in its exchange, the operations the requests named. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everyRequestIsOneTraceOfItsOperation(TestappRecording recording) {
            Map<String, Integer> operations = new TreeMap<>();
            answer("traces_operations", recording, Json.createObject().put("limit", 20)).path("operations")
                    .forEach(operation -> {
                        assertEquals(SERVER, operation.path("kind").asString());
                        assertEquals(HTTP_SERVER_EXCHANGE, operation.path("eventType").asString());
                        operations.put(operation.path("name").asString(), operation.path("count").asInt());
                    });

            assertEquals(new TreeMap<>(recording.traces().requestsByOperation()), operations);
            assertEquals(String.valueOf(recording.traces().exchanges()), scalar(recording, "SELECT count(*) FROM traces"));
        }

        /**
         * The statement the service ran at startup, outside any request, was stamped with no ids: it is
         * part of no trace and no span, and nothing invents an operation for it.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theStatementRunOutsideAnyRequestIsPartOfNoTrace(TestappRecording recording) {
            assertEquals(List.of(List.of("0", "0")), query(recording, "SELECT json_extract_string(fields, '$.traceId'),"
                    + " json_extract_string(fields, '$.spanId') FROM events WHERE json_extract_string(fields, '$.name') = '"
                    + STARTUP_QUERY + "'"));
            assertEquals("0", scalar(recording, "SELECT count(*) FROM trace_spans WHERE name = '" + STARTUP_QUERY + "'"));
        }

        /**
         * Every recorded span is there by name and kind — the steps INTERNAL, the statements CLIENT — and
         * beside them only the blocking events the derivation promoted; none is left without its parent.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everySpanIsThereUnderItsParent(TestappRecording recording) {
            TestappRecording.Traces traces = recording.traces();
            Map<String, String> expected = new TreeMap<>();
            traces.requestsByOperation().forEach((name, count) -> expected.put(name, SERVER + " " + count));
            traces.stepsByName().forEach((name, count) -> expected.put(name, INTERNAL + " " + count));
            traces.statementsByName().forEach((name, count) -> {
                if (!STARTUP_QUERY.equals(name)) {
                    expected.put(name, CLIENT + " " + count);
                }
            });
            Map<String, String> recorded = new TreeMap<>();
            query(recording, "SELECT name, kind, count(*) FROM trace_spans WHERE NOT synthesized GROUP BY ALL")
                    .forEach(row -> recorded.put(row.get(0), row.get(1) + " " + row.get(2)));

            assertEquals(expected, recorded);
            assertEquals(String.valueOf(PROMOTED.get(recording)), scalar(recording,
                    "SELECT count(*) FROM trace_spans WHERE synthesized"));
            assertEquals(traces.exchanges() + traces.spans() + traces.tracedStatements() + PROMOTED.get(recording),
                    answer("traces_overview", recording, Json.createObject()).path("overview").path("totalSpans").asInt());
            assertEquals("0", scalar(recording, "SELECT count(*) FROM trace_spans c WHERE c.parent_span_id IS NOT NULL"
                    + " AND NOT EXISTS (SELECT 1 FROM trace_spans p WHERE p.trace_id = c.trace_id AND p.span_id = c.parent_span_id)"));
        }

        /**
         * Every recorded span hangs where the tracer put it: its parent in the profile is the span its
         * event named, the steps and statements nested as deep as the service nested them.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everySpanHangsUnderTheSpanItsEventNamed(TestappRecording recording) {
            String named = "SELECT json_extract_string(fields, '$.name') AS child, json_extract_string(fields, '$.parentSpanId')::BIGINT"
                    + " AS parent, json_extract_string(fields, '$.traceId')::BIGINT AS trace FROM events WHERE event_type IN ("
                    + STATEMENTS + ", '" + TRACE_SPAN + "') AND json_extract_string(fields, '$.traceId')::BIGINT <> 0";
            String byEvents = "SELECT c.child, p.name, count(*) FROM (" + named + ") c JOIN trace_spans p"
                    + " ON p.trace_id = c.trace AND p.span_id = c.parent GROUP BY ALL ORDER BY ALL";
            String byDerivation = "SELECT c.name, p.name, count(*) FROM trace_spans c JOIN trace_spans p"
                    + " ON p.trace_id = c.trace_id AND p.span_id = c.parent_span_id WHERE NOT c.synthesized"
                    + " AND c.parent_span_id IS NOT NULL GROUP BY ALL ORDER BY ALL";

            assertEquals(query(recording, byEvents), query(recording, byDerivation));
        }
    }

    @Nested
    class Latency {

        /** Each operation's count, median and slowest request are its exchanges'. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void eachOperationIsTimedByItsExchanges(TestappRecording recording) {
            Map<String, List<Long>> exact = new HashMap<>();
            query(recording, "SELECT json_extract_string(fields, '$.name'), count(*), quantile_cont(duration, 0.5)::BIGINT,"
                    + " max(duration)" + EXCHANGES + " GROUP BY 1")
                    .forEach(row -> exact.put(row.get(0), List.of(Long.parseLong(row.get(1)), Long.parseLong(row.get(2)),
                            Long.parseLong(row.get(3)))));
            JsonNode operations = answer("traces_operations", recording, Json.createObject().put("limit", 20)).path("operations");

            for (JsonNode operation : operations) {
                List<Long> figures = exact.get(operation.path("name").asString());
                assertEquals(figures.get(0), operation.path("count").asLong());
                assertEquals(figures.get(1), operation.path("p50Nanos").asLong());
                assertEquals(figures.get(2), operation.path("maxNanos").asLong());
            }
            JsonNode overview = answer("traces_overview", recording, Json.createObject()).path("overview");
            assertEquals(recording.traces().slowestNanos(), overview.path("maxNanos").asLong());
            assertEquals(recording.traces().requestsByOperation().size(), overview.path("distinctOperations").asInt());
        }

        /** The HTTP dashboard reads the same exchanges: each URI as the server templated it, slowest first. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theHttpDashboardReadsTheSameRequests(TestappRecording recording) {
            JsonNode http = answer("http_overview", recording, Json.createObject());
            Map<String, Integer> byUri = new TreeMap<>();
            http.path("endpoints").forEach(endpoint -> byUri.put(endpoint.path("uri").asString(),
                    endpoint.path("requestCount").asInt()));
            Map<String, Integer> exact = new TreeMap<>();
            query(recording, "SELECT json_extract_string(fields, '$.uri'), count(*)" + EXCHANGES + " GROUP BY 1")
                    .forEach(row -> exact.put(row.get(0), Integer.parseInt(row.get(1))));

            assertEquals(exact, byUri);
            assertEquals(recording.traces().exchanges(), http.path("header").path("requestCount").asInt());
            assertEquals(recording.traces().slowestNanos(), http.path("header").path("maxResponseTimeNanos").asLong());
            assertSlowestFirst(http.path("slowRequests"), "responseTimeNanos", recording.traces().slowestNanos());
        }

        /** The JDBC dashboard counts every statement by name — the startup query too — slowest first. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theJdbcDashboardCountsEveryStatement(TestappRecording recording) {
            JsonNode jdbc = answer("jdbc_overview", recording, Json.createObject());
            Map<String, Integer> byName = new TreeMap<>();
            jdbc.path("groups").forEach(group -> group.path("statementNames").forEach(statement ->
                    byName.put(statement.path("name").asString(), statement.path("count").asInt())));

            assertEquals(recording.traces().statements(), jdbc.path("header").path("statementCount").asInt());
            assertEquals(new TreeMap<>(recording.traces().statementsByName()), byName);
            long slowest = Long.parseLong(scalar(recording, "SELECT max(duration) FROM events WHERE event_type IN (" + STATEMENTS + ")"));
            assertSlowestFirst(jdbc.path("slowStatements"), "executionTimeNanos", slowest);
        }

        /** The pool lent a connection for every statement a request ran, and acquired as many. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void thePoolLentAConnectionPerStatement(TestappRecording recording) {
            JsonNode pool = answer("jdbc_pools", recording, Json.createObject()).path("pools").path(0);
            Map<String, Integer> events = new HashMap<>();
            pool.path("events").forEach(event -> events.put(event.path("eventType").asString(), event.path("count").asInt()));

            assertEquals(POOL, pool.path("poolName").asString());
            assertEquals(recording.traces().connectionsAcquired(), events.get(CONNECTION_ACQUIRED));
            assertEquals(recording.traces().connectionsAcquired(), events.get("jeffrey.PooledJdbcConnectionBorrowed"));
        }
    }

    @Nested
    class Attributes {

        /**
         * The root's one attribute — its key holds a dot, read through a quoted JSON path — on every
         * request, its value the run's mode.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void everyRequestCarriesItsRunsMode(TestappRecording recording) {
            JsonNode values = answer("traces_attributeValues", recording, Json.createObject().put("key", MODE));

            assertEquals(1, values.path("values").size(), values::toString);
            assertEquals(recording.mode(), values.path("values").path(0).path("value").asString());
            assertEquals(recording.traces().exchanges(), values.path("values").path(0).path("traceCount").asInt());
            assertEquals(0, values.path("tracesWithoutKey").asInt());
            assertEquals(recording.traces().exchanges(), matching(recording, Json.createObject().put("key", MODE)
                    .put("value", recording.mode())));
            assertEquals(0, matching(recording, Json.createObject().put("key", MODE)
                    .put("operator", "NOT_EQ").put("value", recording.mode())));
        }

        /** Paged to the end, a search on an exchange's own field hands back each of its requests once. */
        @Test
        void aSearchOnAnExchangesFieldPagedToTheEndFindsEachRequestOnce() {
            ObjectNode byUri = Json.createObject().put("key", "uri").put("source", EVENT_FIELD)
                    .put("owner", HTTP_SERVER_EXCHANGE).put("value", PERSONS_BY_COUNT);
            List<String> found = search(DIRECT, byUri);
            Set<String> exact = new TreeSet<>();
            query(DIRECT, "SELECT json_extract_string(fields, '$.traceId')::BIGINT" + EXCHANGES
                    + " AND json_extract_string(fields, '$.uri') = '" + PERSONS_BY_COUNT + "'")
                    .forEach(row -> exact.add(hex(Long.parseLong(row.getFirst()))));

            assertEquals(exact.size(), found.size());
            assertEquals(exact, new TreeSet<>(found));
        }

        /** Each statement's SQL is a value of its own, carried by as many traces as ran it. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void eachStatementsSqlIsCarriedByTheTracesThatRanIt(TestappRecording recording) {
            JsonNode values = answer("traces_attributeValues", recording, Json.createObject().put("key", "sql")
                    .put("source", EVENT_FIELD).put("owner", JDBC_QUERY).put("limit", 10)).path("values");
            Map<String, Integer> answered = new TreeMap<>();
            values.forEach(value -> answered.put(value.path("value").asString(), value.path("traceCount").asInt()));
            Map<String, Integer> exact = new TreeMap<>();
            query(recording, "SELECT json_extract_string(fields, '$.sql'), count(DISTINCT json_extract_string(fields, '$.traceId'))"
                    + " FROM events WHERE event_type = '" + JDBC_QUERY + "' AND json_extract_string(fields, '$.traceId') <> '0'"
                    + " GROUP BY 1").forEach(row -> exact.put(row.get(0), Integer.parseInt(row.get(1))));

            assertEquals(exact, answered);
        }

        /**
         * An insert's parameters — the longest text of its event, which the parse stores apart from it —
         * are found whole by a search, in the one trace that ran them.
         */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void aPooledTextIsFoundWholeByASearch(TestappRecording recording) {
            List<String> pooled = query(recording, "SELECT json_extract_string(v.fields, '$.params'),"
                    + " json_extract_string(v.fields, '$.traceId')::BIGINT FROM events v WHERE v.event_type = '" + JDBC_UPDATE + "'"
                    + " AND v.start_timestamp = (SELECT min(start_timestamp) FROM events_raw WHERE event_type = '" + JDBC_UPDATE + "'"
                    + " AND pooled_field = 'params')").getFirst();

            JsonNode found = answer("traces_attributeSearch", recording, Json.createObject().put("key", "params")
                    .put("source", EVENT_FIELD).put("owner", JDBC_UPDATE).put("value", pooled.get(0)).put("limit", 5));

            assertTrue(pooled.get(0).length() >= POOLED_LENGTH, pooled::toString);
            assertEquals(1, found.path("totalMatching").asInt(), found::toString);
            assertEquals(hex(Long.parseLong(pooled.get(1))), found.path("matches").path(0).path("trace").path("traceId").asString());
        }

        private int matching(TestappRecording recording, ObjectNode condition) {
            return answer("traces_attributeSearch", recording, condition.put("limit", 1)).path("totalMatching").asInt();
        }
    }

    /** The one change between the runs, seen through the traces. */
    @Nested
    class TheChangeBetweenTheRuns {

        /** Only the DOM run's requests build a JSON tree, and its breakdown says where. */
        @Test
        void onlyTheDomRunsRequestsBuildAJsonTree() {
            assertTrue(breakdown(DOM, GET_PERSONS).contains(TO_JSON_TREE), () -> breakdown(DOM, GET_PERSONS).toString());
            assertFalse(breakdown(DIRECT, GET_PERSONS).contains(TO_JSON_TREE), () -> breakdown(DIRECT, GET_PERSONS).toString());
            assertEquals(DOM.traces().stepsByName().get(TO_JSON_TREE),
                    Integer.parseInt(scalar(DOM, "SELECT count(*) FROM trace_spans WHERE name = '" + TO_JSON_TREE + "'")));
            assertEquals("0", scalar(DIRECT, "SELECT count(*) FROM trace_spans WHERE name = '" + TO_JSON_TREE + "'"));
        }

        private List<String> breakdown(TestappRecording recording, String operation) {
            String document = answer("traces_operationExport", recording, Json.createObject().put("name", operation)
                    .put("kind", SERVER).put("eventType", HTTP_SERVER_EXCHANGE)).path("markdown").asString();
            int start = document.indexOf("## Span breakdown");
            String section = document.substring(start, document.indexOf("\n## ", start + 1));
            List<String> names = new ArrayList<>();
            Matcher line = BREAKDOWN_NAME.matcher(section);
            while (line.find()) {
                names.add(line.group(1));
            }
            return names;
        }
    }

    /** The CPU inside a request, the samples in async-profiler's chain scoped by the request in the JDK's. */
    @Nested
    class CpuInsideRequests {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void aRequestsCpuGraphHoldsItsThreadsSamples(TestappRecording recording) {
            List<String> busiest = query(recording, "SELECT r.trace_id, r.span_id, count(*) AS samples"
                    + " FROM trace_spans r JOIN traces t ON t.trace_id = r.trace_id AND t.root_span_id = r.span_id"
                    + " JOIN events e ON e.event_type = '" + EXECUTION_SAMPLE + "' AND e.thread_hash = r.thread_hash"
                    + " AND epoch_us(e.start_timestamp) BETWEEN epoch_us(r.start_timestamp)"
                    + " AND epoch_us(r.start_timestamp) + r.duration // 1000"
                    + " GROUP BY ALL ORDER BY samples DESC, r.trace_id LIMIT 1").getFirst();
            String traceId = hex(Long.parseLong(busiest.get(0)));
            String spanId = hex(Long.parseLong(busiest.get(1)));

            int whole = spanSamples(recording, traceId, spanId, false);
            int selfOnly = spanSamples(recording, traceId, spanId, true);

            assertEquals(Integer.parseInt(busiest.get(2)), whole);
            assertTrue(selfOnly <= whole, () -> selfOnly + " of " + whole);
        }

        private int spanSamples(TestappRecording recording, String traceId, String spanId, boolean selfOnly) {
            String document = answer("traces_spanFlamegraphExport", recording, Json.createObject()
                    .put("traceId", traceId).put("spanId", spanId).put("eventType", EXECUTION_SAMPLE)
                    .put("selfOnly", selfOnly)).path("markdown").asString();
            Matcher total = SAMPLES_TOTAL.matcher(document);
            return total.find() ? Integer.parseInt(total.group(1)) : 0;
        }
    }

    /** The UI's trace pages answer from the same derivation as the tools. */
    @Nested
    class TheUiPages {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        void theUiReadsTheSameTracesAsTheTools(TestappRecording recording) {
            JsonNode overview = answer("traces_overview", recording, Json.createObject()).path("overview");
            JsonNode page = microscope.get("/api/internal/profiles/" + profile(recording) + "/traces/overview");
            JsonNode operations = microscope.get("/api/internal/profiles/" + profile(recording) + "/traces/operations");

            assertEquals(overview.path("totalTraces").asLong(), page.path("totalTraces").asLong(), page::toString);
            assertEquals(overview.path("totalSpans").asLong(), page.path("totalSpans").asLong(), page::toString);
            assertEquals(recording.traces().requestsByOperation().size(), operations.path("totalMatching").asInt(),
                    operations::toString);
        }
    }

    /** Every trace of a search, paged through its cursor. */
    private List<String> search(TestappRecording recording, ObjectNode condition) {
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

    private static void assertSlowestFirst(JsonNode rows, String field, long slowest) {
        List<Long> times = new ArrayList<>();
        rows.forEach(row -> times.add(row.path(field).asLong()));
        assertFalse(times.isEmpty(), field);
        assertEquals(times.stream().sorted((a, b) -> Long.compare(b, a)).toList(), times, field);
        assertEquals(slowest, times.getFirst(), field);
    }
}
