/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.jfr.events.grpc.GrpcClientExchangeEvent;
import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.trace.Span;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.SpanConventionKeys;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceContextCategory;
import cafe.jeffrey.provider.profile.api.TraceListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.provider.profile.api.TraceOperationSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TracePage;
import cafe.jeffrey.provider.profile.api.TracePauseRecord;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.provider.profile.api.TraceSpanContextRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.provider.profile.api.TraceTimelineBucketRecord;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcTraceRepositoryTest {

    private static final long MS = 1_000_000L;
    private static final long US_PER_MS = 1_000L;
    private static final long SLOW_TRACE = Long.MAX_VALUE;
    private static final long FAST_TRACE = Long.MIN_VALUE;
    private static final long NEGATIVE_SPAN_ID = -8113938001533374712L;
    /** The HTTP exchange the whole slow trace hangs off, and the parent of both statements. */
    private static final long ROOT_SPAN_ID = 111L;
    /** The span of an event that carries trace ids but none of the rest of the span shape. */
    private static final long BARE_SPAN_ID = 444L;
    /** 2025-01-15T10:00:00Z, the fixture's origin, as epoch millis. */
    private static final long EPOCH_10_00_00 = 1736935200000L;

    private static final String HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange";

    private static final TraceOperationId FLAMEGRAPH_OPERATION = operation(
            "POST /api/internal/profiles/{profileId}/flamegraph", "SERVER", HTTP_SERVER_EXCHANGE);
    private static final TraceOperationId HEALTH_OPERATION =
            operation("GET /api/internal/health", "SERVER", HTTP_SERVER_EXCHANGE);

    private static TraceOperationId operation(String name, String kind, String eventType) {
        return new TraceOperationId(name, kind, eventType);
    }

    private static JdbcTraceRepository derived(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
        JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
        repository.derive();
        return repository;
    }

    @Nested
    @DisplayName("Operation identity")
    class OperationIdentity {

        private static final TraceOperationId INBOUND_HEALTH = HEALTH_OPERATION;
        private static final TraceOperationId OUTBOUND_HEALTH =
                operation("GET /api/internal/health", "CLIENT", "jeffrey.HttpClientExchange");
        private static final TraceOperationId ORDERS =
                operation("POST /orders", "SERVER", HTTP_SERVER_EXCHANGE);

        private JdbcTraceRepository withBothDirections(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-operation-identity-traces.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        @Test
        @DisplayName("an inbound and an outbound call of the same name are two operations")
        void sameNameDifferentDirection(DataSource dataSource) throws SQLException {
            List<TraceOperationRecord> health = withBothDirections(dataSource).operations(TraceOperationListQuery.busiest(100)).operations().stream()
                    .filter(row -> "GET /api/internal/health".equals(row.name()))
                    .toList();

            assertEquals(2, health.size(), "grouping by name alone merged these into one row");
            assertEquals(List.of("CLIENT", "SERVER"),
                    health.stream().map(TraceOperationRecord::kind).sorted().toList());
            assertTrue(health.stream().allMatch(row -> row.count() == 1),
                    "neither row may absorb the other's traces");
        }

        @Test
        @DisplayName("drilling into one direction does not pick up the other's traces")
        void drillDownIsScopedToOneDirection(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = withBothDirections(dataSource);

            List<TraceSummaryRecord> inbound = repository.tracesOfOperation(INBOUND_HEALTH, 100);
            List<TraceSummaryRecord> outbound = repository.tracesOfOperation(OUTBOUND_HEALTH, 100);

            assertEquals(List.of(FAST_TRACE), inbound.stream().map(TraceSummaryRecord::traceId).toList());
            assertEquals(List.of(1001L), outbound.stream().map(TraceSummaryRecord::traceId).toList());
        }

        @Test
        @DisplayName("an operation that calls itself keeps its nested occurrences in the breakdown")
        void recursionSurvivesTheBreakdown(DataSource dataSource) throws SQLException {
            // Excluding the root by name matched the nested span too and dropped it entirely, so the
            // breakdown of a recursive handler hid the recursion that made it worth opening.
            List<TraceOperationSpanRecord> spans =
                    withBothDirections(dataSource).spanBreakdownOfOperation(ORDERS, 10);

            TraceOperationSpanRecord nested = spans.stream()
                    .filter(span -> "POST /orders".equals(span.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the nested self-call must be listed"));
            assertEquals(1, nested.occurrences(), "the root itself is still excluded");
            assertEquals(50 * MS, nested.totalNanos());

            assertTrue(spans.stream().anyMatch(span -> "orders.persist".equals(span.name())));
        }
    }

    @Nested
    @DisplayName("Operation intervals")
    class OperationIntervals {

        @Test
        @DisplayName("one interval per thread the operation's traces touched")
        void groupsByThread(DataSource dataSource) throws SQLException {
            // The slow trace runs on the virtual request thread and forks one span onto the pool.
            List<SpanInterval> intervals = derived(dataSource).operationIntervals(FLAMEGRAPH_OPERATION);

            assertEquals(2, intervals.size(), "the two threads the trace touched");

            SpanInterval request = intervals.stream()
                    .filter(interval -> interval.threadHash() == 3001L).findFirst().orElseThrow();
            assertEquals(EPOCH_10_00_00, request.fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 120, request.toEpochMillis(),
                    "the exchange's own window bounds the statements inside it");

            SpanInterval forked = intervals.stream()
                    .filter(interval -> interval.threadHash() == 3002L).findFirst().orElseThrow();
            assertEquals(EPOCH_10_00_00 + 60, forked.fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 80, forked.toEpochMillis());
        }

        @Test
        @DisplayName("an operation nobody ran scopes to nothing rather than to everything")
        void unknownOperationHasNoIntervals(DataSource dataSource) throws SQLException {
            assertTrue(derived(dataSource)
                    .operationIntervals(operation("GET /nope", "SERVER", HTTP_SERVER_EXCHANGE))
                    .isEmpty());
        }

        /** The same trace, plus the scope events a re-entered span leaves behind. */
        private JdbcTraceRepository withScopes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-scopes.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        @Test
        @DisplayName("a thread the span only ran on gets an interval of its own")
        void scopesContributeTheirOwnThread(DataSource dataSource) throws SQLException {
            // Span 111 is committed by the request thread, so without its scope events the callback
            // thread is invisible and the flamegraph never samples it.
            List<SpanInterval> intervals = withScopes(dataSource).operationIntervals(FLAMEGRAPH_OPERATION);

            assertEquals(3, intervals.size(), "the request thread, the forked pool thread, and the callback thread");

            SpanInterval callback = intervals.stream()
                    .filter(interval -> interval.threadHash() == 3003L).findFirst()
                    .orElseThrow(() -> new AssertionError("the re-entered thread must get an interval"));
            assertEquals(EPOCH_10_00_00 + 90, callback.fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 110, callback.toEpochMillis());
        }

        @Test
        @DisplayName("a scope on the span's own thread does not widen that thread's window")
        void scopeOnTheSameThreadChangesNothing(DataSource dataSource) throws SQLException {
            List<SpanInterval> intervals = withScopes(dataSource).operationIntervals(FLAMEGRAPH_OPERATION);

            SpanInterval request = intervals.stream()
                    .filter(interval -> interval.threadHash() == 3001L).findFirst().orElseThrow();
            assertEquals(EPOCH_10_00_00, request.fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 120, request.toEpochMillis(),
                    "the scope sits inside the exchange, so the exchange still bounds the thread");
        }

        /** The same trace, plus a second, later stint of the slow trace on the pool thread. */
        private JdbcTraceRepository withDisjointActivity(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-disjoint-thread-activity.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        @Test
        @DisplayName("a thread active twice with a gap keeps two intervals rather than one spanning it")
        void disjointActivityKeepsTheGap(DataSource dataSource) throws SQLException {
            // Collapsing a thread to MIN/MAX handed the flamegraph one window across the idle gap,
            // absorbing whatever unrelated work the thread did between its two stints on the trace.
            List<SpanInterval> intervals =
                    withDisjointActivity(dataSource).operationIntervals(FLAMEGRAPH_OPERATION);

            List<SpanInterval> pool = intervals.stream()
                    .filter(interval -> interval.threadHash() == 3002L)
                    .sorted(Comparator.comparingLong(SpanInterval::fromEpochMillis))
                    .toList();

            assertEquals(2, pool.size(), "one interval per stint, not one spanning the gap");
            assertEquals(EPOCH_10_00_00 + 60, pool.get(0).fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 80, pool.get(0).toEpochMillis());
            assertEquals(EPOCH_10_00_00 + 100, pool.get(1).fromEpochMillis());
            assertEquals(EPOCH_10_00_00 + 110, pool.get(1).toEpochMillis());
        }

        @Test
        @DisplayName("a scope belonging to another trace is not pulled in")
        void scopesAreScopedToTheirOwnSpan(DataSource dataSource) throws SQLException {
            // The scopes name span 111, which belongs to the flamegraph operation; the health check
            // is a different trace and must not inherit the callback thread.
            List<SpanInterval> health = withScopes(dataSource).operationIntervals(HEALTH_OPERATION);

            assertTrue(health.stream().noneMatch(interval -> interval.threadHash() == 3003L));
        }
    }

    @Nested
    @DisplayName("Derivation")
    class Derivation {

        @Test
        @DisplayName("deriving twice lands where deriving once did")
        void derivationIsIdempotent(DataSource dataSource) throws SQLException {
            // A re-import used to double every span and then fail on the traces primary key, leaving
            // spans behind that no trace header accounted for.
            JdbcTraceRepository repository = derived(dataSource);
            List<TraceSpanRecord> once = repository.spansOf(SLOW_TRACE);
            TraceOverviewRecord overviewOnce = repository.overview();

            repository.derive();

            assertEquals(once, repository.spansOf(SLOW_TRACE));
            assertEquals(overviewOnce, repository.overview());
        }

        @Test
        @DisplayName("a trace's duration keeps the nanoseconds its spans were recorded with")
        void durationKeepsNanosecondPrecision(DataSource dataSource) throws SQLException {
            // The exchange starts at .000 and the last span ends 80ms750us in, so a derivation that
            // rounded to whole milliseconds anywhere would report a different number here.
            TraceSummaryRecord slow = derived(dataSource).summaryOf(SLOW_TRACE).orElseThrow();

            assertEquals(120 * MS, slow.durationNanos());
        }

        @Test
        @DisplayName("a trace is flagged for flamegraphs only when a span of it ran on a platform thread")
        void flagsPlatformSpans(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertTrue(repository.summaryOf(SLOW_TRACE).orElseThrow().hasPlatformSpan(),
                    "one span forked onto the pool thread");
            assertFalse(repository.summaryOf(FAST_TRACE).orElseThrow().hasPlatformSpan(),
                    "the fast trace never left the virtual request thread");
        }

        @Test
        @DisplayName("a span whose thread did not resolve is not counted as platform")
        void unresolvedThreadIsNotPlatform(DataSource dataSource) throws SQLException {
            // `threads` has no row for this hash, so nothing says a sample could be attributed to it.
            // Counting the unknown as platform promised a flamegraph that comes back empty.
            TestUtils.executeSql(dataSource, "sql/events/insert-unresolved-thread-trace.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            assertTrue(repository.traces(TraceListQuery.slowest(100)).traces().stream().noneMatch(TraceSummaryRecord::hasPlatformSpan),
                    "an unresolved thread cannot promise samples");

            TraceOperationThreadsRecord threads = repository.threadsOfOperation(
                    operation("orphan.work", "INTERNAL", "jeffrey.TraceSpan"));
            assertEquals(0, threads.platformSpans());
            assertEquals(0, threads.virtualSpans());
            assertEquals(1, threads.unknownSpans(), "counted as unknown, not folded into either side");
        }

        @Test
        @DisplayName("lifts every traced event type into spans and leaves the rest behind")
        void derivesOnlyTracedEvents(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);

            // The HTTP exchange, the two JDBC queries and the hand-written span -- but neither the
            // untraced JDBC row nor the execution sample.
            assertEquals(4, spans.size());
            assertTrue(spans.stream().noneMatch(span -> "untraced".equals(span.name())));
            assertTrue(spans.stream().noneMatch(span -> span.eventType().startsWith("jdk.")));
        }

        @Test
        @DisplayName("every span keeps the identity its event recorded")
        void keepsRecordedIdentity(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);
            Map<String, TraceSpanRecord> byName = spans.stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            // Nothing is invented here any more: Tracer minted a span id per event, so the
            // derivation reads all three ids straight out of the JSON.
            assertEquals(ROOT_SPAN_ID, byName.get("POST /api/internal/profiles/{profileId}/flamegraph").spanId());
            assertEquals(NEGATIVE_SPAN_ID, byName.get("flamegraph.generate").spanId());
            assertEquals(ROOT_SPAN_ID, byName.get("listSpans").parentSpanId());
            assertEquals(ROOT_SPAN_ID, byName.get("countSpans").parentSpanId());
            assertEquals(spans.size(), spans.stream().map(TraceSpanRecord::spanId).distinct().count(),
                    "a span id has to identify exactly one span");
        }

        @Test
        @DisplayName("a span carries whether its thread was virtual")
        void reportsVirtualThreads(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            // Samples are attributed to the carrier, so this is what decides whether a span can have
            // a flamegraph at all -- the UI hides the tab rather than opening an empty one.
            assertTrue(byName.get("POST /api/internal/profiles/{profileId}/flamegraph").isVirtual(),
                    "the request ran on a virtual thread");
            assertTrue(byName.get("listSpans").isVirtual(),
                    "a statement issued on the request's thread inherits it");
            assertFalse(byName.get("flamegraph.generate").isVirtual(),
                    "the forked span ran on a platform pool thread");
        }

        @Test
        @DisplayName("round-trips 64-bit ids through JSON without losing precision or sign")
        void preservesIdBoundaries(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);

            assertTrue(spans.stream().allMatch(span -> span.traceId() == Long.MAX_VALUE));
            assertTrue(spans.stream().anyMatch(span -> span.spanId() == NEGATIVE_SPAN_ID),
                    "a negative span id must survive the JSON round trip");
            assertFalse(repository.traces(TraceListQuery.slowest(10)).traces().stream()
                    .noneMatch(trace -> trace.traceId() == Long.MIN_VALUE));
        }

        @Test
        @DisplayName("a root span has no parent, a nested one points at it")
        void normalisesTheRootParent(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            TraceSpanRecord root = byName.get("POST /api/internal/profiles/{profileId}/flamegraph");
            assertNull(root.parentSpanId(), "0 on the wire means absent, and must become NULL here");
            assertEquals(root.spanId(), byName.get("listSpans").parentSpanId());
            assertEquals(root.spanId(), byName.get("flamegraph.generate").parentSpanId());
        }

        @Test
        @DisplayName("every span gets the kind its type's convention gives it")
        void readsNameAndKindFromTheEvent(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            // Three event types, one uniform shape: an exchange is the inbound side by construction,
            // a statement is always a call out, and a hand-written span is whatever Tracer was told.
            assertEquals("SERVER", byName.get("POST /api/internal/profiles/{profileId}/flamegraph").kind());
            assertEquals("CLIENT", byName.get("listSpans").kind());
            assertEquals("INTERNAL", byName.get("flamegraph.generate").kind());
        }

        @Test
        @DisplayName("a hand-written span's attributes are unwrapped from the field that carries them")
        void unwrapsHandWrittenAttributes(DataSource dataSource) throws SQLException {
            // TraceSpanEvent.attributes is itself a JSON object encoded as a string, so it is taken
            // out whole rather than merged with the event's own columns.
            Map<String, TraceSpanRecord> byName = derived(dataSource).spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            assertEquals("{\"eventType\":\"jdk.ExecutionSample\",\"depth\":7}",
                    byName.get("flamegraph.generate").attributes());
        }

        @Test
        @DisplayName("an instrumented event's own fields become the span's event fields")
        void derivesEventFieldsFromTheEvent(DataSource dataSource) throws SQLException {
            // A statement attaches no attribute map; what makes it worth reading is the SQL and the
            // row count, which are declared fields of the event itself.
            Map<String, TraceSpanRecord> byName = derived(dataSource).spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            String attributes = byName.get("listSpans").eventFields();

            assertTrue(attributes.contains("\"sql\":\"SELECT * FROM spans\""), attributes);
            assertTrue(attributes.contains("\"rows\":42"), attributes);
            assertTrue(attributes.contains("\"group\":\"PROFILE_EVENTS\""), attributes);
        }

        @Test
        @DisplayName("the plumbing every traced event carries is stripped out of the event fields")
        void stripsPlumbingFromEventFields(DataSource dataSource) throws SQLException {
            // Ids, JFR's own columns and the name the span already carries are not detail about the
            // operation, and repeating them would bury the two or three keys that are.
            Map<String, TraceSpanRecord> byName = derived(dataSource).spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            String attributes = byName.get("listSpans").eventFields();

            assertFalse(attributes.contains("traceId"), attributes);
            assertFalse(attributes.contains("spanId"), attributes);
            assertFalse(attributes.contains("parentSpanId"), attributes);
            assertFalse(attributes.contains("startTime"), attributes);
            assertFalse(attributes.contains("duration"), attributes);
            assertFalse(attributes.contains("\"name\""), attributes);
            assertFalse(attributes.contains("\"status\""), attributes);
        }

        @Test
        @DisplayName("a response code under `status` is detail, not plumbing, and is kept")
        void keepsAResponseCodeThatIsNotASpanStatus(DataSource dataSource) throws SQLException {
            // Where an exchange records no `statusCode`, `status` holds the response code and is the
            // only record of it. Stripping that key unconditionally took the 500 out of the span
            // detail of every recording that spells the outcome that way.
            TestUtils.executeSql(dataSource, "sql/events/insert-unnamed-exchange-events.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            TraceSpanRecord failed = repository.spansOf(502L).getFirst();

            assertEquals("ERROR", failed.status(), "the span is still judged by the code");
            assertTrue(failed.eventFields().contains("\"status\":500"), failed.eventFields());
        }

        @Test
        @DisplayName("an exchange keeps the parts of its name that are also detail")
        void keepsExchangeDetailInEventFields(DataSource dataSource) throws SQLException {
            // The span's name is built from method and URI, but both stay in the attributes: the
            // name is for scanning the waterfall, the attributes for reading one span closely.
            TraceSpanRecord exchange = derived(dataSource).spansOf(FAST_TRACE).getFirst();

            assertTrue(exchange.eventFields().contains("\"statusCode\":500"), exchange.eventFields());
            assertTrue(exchange.eventFields().contains("\"method\":\"GET\""), exchange.eventFields());
            assertTrue(exchange.eventFields().contains("\"uri\":\"/api/internal/health\""),
                    exchange.eventFields());
        }

        @Test
        @DisplayName("attributes and event fields are never both set on one span")
        void keepsTheTwoKindsOfDetailApart(DataSource dataSource) throws SQLException {
            // They are different kinds of thing and no event carries both: an attribute map is what
            // a hand-written span attached to itself, event fields are its event type's schema.
            // Merging them into one column is what made a statement's `sql` look hand-attached.
            Map<String, TraceSpanRecord> byName = derived(dataSource).spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            TraceSpanRecord handWritten = byName.get("flamegraph.generate");
            assertNotNull(handWritten.attributes());
            assertNull(handWritten.eventFields(),
                    "jeffrey.TraceSpan declares no fields the span row does not already carry");

            TraceSpanRecord statement = byName.get("listSpans");
            assertNotNull(statement.eventFields());
            assertNull(statement.attributes(),
                    "a statement has no attribute map to attach one to");
        }

        @Test
        @DisplayName("the status each event recorded is what the trace counts as a failure")
        void countsRecordedFailures(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            TraceSpanRecord failed = byName.get("flamegraph.generate");
            assertEquals("ERROR", failed.status());
            assertEquals("java.lang.IllegalStateException", failed.errorType());
            assertEquals("UNSET", byName.get("listSpans").status());

            // The failing exchange decided it had failed when it committed -- HTTP 500, in its case.
            TraceSummaryRecord fast = repository.traces(TraceListQuery.slowest(10)).traces().stream()
                    .filter(trace -> trace.traceId() == FAST_TRACE).findFirst().orElseThrow();
            assertEquals(1, fast.errorCount());
        }

        @Test
        @DisplayName("rows claiming the same span id are deduped to the earliest occurrence")
        void dedupesRepeatedSpanIds(DataSource dataSource) throws SQLException {
            // A re-imported chunk or instrumentation that reused an id. Without the dedupe, both
            // rows landed in trace_spans, span_count counted them both, and the waterfall drew one.
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-duplicate-span-events.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);

            assertEquals(spans.size(), spans.stream().map(TraceSpanRecord::spanId).distinct().count(),
                    "a span id has to identify exactly one span");
            TraceSpanRecord kept = spans.stream()
                    .filter(span -> span.spanId() == 112L).findFirst().orElseThrow();
            assertEquals("listSpans", kept.name(),
                    "the earliest occurrence wins, the row the waterfall would have drawn anyway");
            assertEquals(spans.size(), repository.summaryOf(SLOW_TRACE).orElseThrow().spanCount(),
                    "span_count must agree with the rows the waterfall draws");
        }

        @Test
        @DisplayName("an event no convention names takes its event type for a name")
        void fallsBackForAnIncompleteShape(DataSource dataSource) throws SQLException {
            // Third-party instrumentation that stamped only the ids: no convention places it and it
            // named nothing itself. Dropping it would lose a real part of the trace, so it takes its
            // event type for a name and the neutral kind and status.
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-bare-traced-event.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            TraceSpanRecord bare = repository.spansOf(SLOW_TRACE).stream()
                    .filter(span -> span.spanId() == BARE_SPAN_ID)
                    .findFirst()
                    .orElseThrow();

            assertEquals("jeffrey.ThirdPartyEvent", bare.name());
            assertEquals("INTERNAL", bare.kind());
            assertEquals("UNSET", bare.status());
        }
    }

    @Nested
    @DisplayName("Span naming conventions")
    class SpanNaming {

        private static final long HEALTHY_TRACE = 501L;
        private static final long FAILED_TRACE = 502L;
        private static final long GRPC_TRACE = 503L;

        private static final String HEALTH_ENDPOINT = "GET /api/internal/health";
        private static final String GRPC_CALL = "jeffrey.api.v1.ProjectService/List";

        /** Exchanges that recorded their own fields and no span shape — nothing to read a name from. */
        private JdbcTraceRepository unnamedExchanges(DataSource dataSource) throws SQLException {
            return derivedFrom(dataSource, "sql/events/insert-unnamed-exchange-events.sql");
        }

        /** The same endpoint recorded both ways, plus the cases where field and convention differ. */
        private JdbcTraceRepository mixedShapes(DataSource dataSource) throws SQLException {
            return derivedFrom(dataSource, "sql/events/insert-mixed-shape-exchanges.sql");
        }

        private JdbcTraceRepository derivedFrom(DataSource dataSource, String fixture) throws SQLException {
            TestUtils.executeSql(dataSource, fixture);
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        private Map<Long, TraceSpanRecord> spansById(JdbcTraceRepository repository, long traceId) {
            return repository.spansOf(traceId).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::spanId, Function.identity()));
        }

        @Test
        @DisplayName("an exchange is named by its method and URI")
        void namesAnExchangeByMethodAndUri(DataSource dataSource) throws SQLException {
            TraceSpanRecord exchange = spansById(unnamedExchanges(dataSource), HEALTHY_TRACE).get(5011L);

            assertEquals(HEALTH_ENDPOINT, exchange.name());
            assertEquals("SERVER", exchange.kind(), "the direction is a property of the event type");
            assertEquals("UNSET", exchange.status(), "200 is not a failure, and not a span status either");
        }

        @Test
        @DisplayName("a gRPC call is named by its service and method, and fails on a non-OK code")
        void namesAGrpcCallByServiceAndMethod(DataSource dataSource) throws SQLException {
            TraceSpanRecord call = spansById(unnamedExchanges(dataSource), GRPC_TRACE).get(5031L);

            assertEquals(GRPC_CALL, call.name());
            assertEquals("SERVER", call.kind());
            assertEquals("ERROR", call.status(), "UNAVAILABLE is a failed call");
        }

        @Test
        @DisplayName("a response code is read as an outcome rather than as a span status")
        void readsTheResponseCodeAsAnOutcome(DataSource dataSource) throws SQLException {
            // Where an exchange records no `statusCode`, its code is what `status` holds -- and that
            // is the same key a self-describing event puts a span status under. Reading `statusCode`
            // first and `status` only where it cannot be a span status is what tells the two apart.
            JdbcTraceRepository repository = unnamedExchanges(dataSource);

            TraceSpanRecord failed = spansById(repository, FAILED_TRACE).get(5021L);
            assertEquals("ERROR", failed.status());
            assertEquals(1, repository.summaryOf(FAILED_TRACE).orElseThrow().errorCount());
            assertEquals(0, repository.summaryOf(HEALTHY_TRACE).orElseThrow().errorCount());
        }

        @Test
        @DisplayName("a statement keeps its own name and is a client call")
        void namesAStatementAfterItself(DataSource dataSource) throws SQLException {
            // A statement names itself -- there is no convention for it beyond being a call out to
            // something else -- and `isSuccess` is what an outcome was before it had a status.
            JdbcTraceRepository repository = unnamedExchanges(dataSource);

            TraceSpanRecord succeeded = spansById(repository, HEALTHY_TRACE).get(5012L);
            assertEquals("listSpans", succeeded.name());
            assertEquals("CLIENT", succeeded.kind());
            assertEquals("UNSET", succeeded.status());

            assertEquals("ERROR", spansById(repository, GRPC_TRACE).get(5032L).status());
        }

        @Test
        @DisplayName("the operations are the endpoints, not the event types they were recorded by")
        void groupsOperationsByEndpoint(DataSource dataSource) throws SQLException {
            // The regression this guards: with no name to read, every request in the recording
            // collapsed into one INTERNAL operation called `jeffrey.HttpServerExchange`.
            List<TraceOperationRecord> operations =
                    unnamedExchanges(dataSource).operations(TraceOperationListQuery.busiest(100)).operations();

            assertEquals(List.of(HEALTH_ENDPOINT, GRPC_CALL),
                    operations.stream().map(TraceOperationRecord::name).sorted().toList());
            assertTrue(operations.stream().noneMatch(row -> row.name().startsWith("jeffrey.Http")),
                    "an event type is not an operation name");

            TraceOperationRecord health = operations.stream()
                    .filter(row -> HEALTH_ENDPOINT.equals(row.name()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(2, health.count(), "both requests of the endpoint belong to one operation");
            assertEquals(1, health.errorCount());
            assertEquals("SERVER", health.kind());
        }

        @Test
        @DisplayName("drilling into an operation finds the traces it was derived from")
        void drillsDownIntoADerivedOperation(DataSource dataSource) throws SQLException {
            List<TraceSummaryRecord> traces = unnamedExchanges(dataSource)
                    .tracesOfOperation(operation(HEALTH_ENDPOINT, "SERVER", HTTP_SERVER_EXCHANGE), 100);

            assertEquals(List.of(HEALTHY_TRACE, FAILED_TRACE),
                    traces.stream().map(TraceSummaryRecord::traceId).toList());
        }

        @Test
        @DisplayName("an endpoint recorded by either instrumentation is one operation")
        void eitherShapeIsOneOperation(DataSource dataSource) throws SQLException {
            // The whole point of deriving rather than reading back: a recorded name is this same rule
            // evaluated by whichever version recorded it, and an endpoint that only some recordings
            // carry the answer for is an endpoint split across two rows of Trace Operations.
            TraceOperationRecord health = mixedShapes(dataSource)
                    .operations(TraceOperationListQuery.busiest(100)).operations().stream()
                    .filter(row -> HEALTH_ENDPOINT.equals(row.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("both recordings must land under one name"));

            assertEquals(2, health.count());
            assertEquals("SERVER", health.kind());
        }

        @Test
        @DisplayName("a self-describing exchange derives exactly the name it recorded")
        void aCurrentRecordingIsUnchanged(DataSource dataSource) throws SQLException {
            // The guard that deriving changes nothing for a recording that names itself: this fails
            // the moment the SQL and describeSpan() disagree about what an exchange is called.
            JdbcTraceRepository repository = derived(dataSource);

            TraceSummaryRecord slow = repository.summaryOf(SLOW_TRACE).orElseThrow();
            assertEquals("POST /api/internal/profiles/{profileId}/flamegraph", slow.rootName());
            assertEquals("SERVER", slow.rootKind());

            TraceSummaryRecord fast = repository.summaryOf(FAST_TRACE).orElseThrow();
            assertEquals("GET /api/internal/health", fast.rootName());
            assertEquals(1, fast.errorCount(), "500 is a failure whichever side works it out");
        }

        @Test
        @DisplayName("the convention outranks a name the recording disagrees with")
        void theConventionOutranksARecordedName(DataSource dataSource) throws SQLException {
            TraceSpanRecord exchange = spansById(mixedShapes(dataSource), 603L).get(6031L);

            assertEquals("POST /orders", exchange.name(),
                    "an exchange is named by what it did, not by what one library version called it");
        }

        @Test
        @DisplayName("an error the instrumentation recorded outranks a successful response code")
        void aRecordedErrorOutranksTheCode(DataSource dataSource) throws SQLException {
            // The one outcome a code cannot carry: the exchange threw and still answered 200.
            JdbcTraceRepository repository = mixedShapes(dataSource);

            TraceSpanRecord threw = spansById(repository, 604L).get(6041L);
            assertEquals("ERROR", threw.status());
            assertEquals("java.lang.IllegalStateException", threw.errorType());
            assertEquals(1, repository.summaryOf(604L).orElseThrow().errorCount());
        }

        @Test
        @DisplayName("400 is the first response code that fails the span")
        void fourHundredIsTheFirstFailure(DataSource dataSource) throws SQLException {
            // The threshold is spelled once in the event class and once in the SQL; nothing else
            // pins them to each other.
            JdbcTraceRepository repository = mixedShapes(dataSource);

            assertEquals("UNSET", spansById(repository, 605L).get(6051L).status());
            assertEquals("ERROR", spansById(repository, 606L).get(6061L).status());
        }
    }

    @Nested
    @DisplayName("Conventions declared in the recording")
    class DeclaredConventions {

        private static final String PUBLISH_OPERATION = "PUBLISH orders";

        private JdbcTraceRepository declared(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-declared-convention-events.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        private TraceSummaryRecord root(JdbcTraceRepository repository, long traceId) {
            return repository.summaryOf(traceId).orElseThrow();
        }

        @Test
        @DisplayName("a plain-commit third-party event is named by its declared template")
        void namesAPlainCommitEvent(DataSource dataSource) throws SQLException {
            // The case nothing else can serve: describeSpan() never ran, so no name was recorded.
            // The template travelled in the recording's metadata, and Jeffrey has no code that
            // knows com.acme.KafkaPublish. The verdict is a different story on purpose: it is the
            // writer's statement, so the 503 sitting in deliveryCode is NOT read as a failure --
            // failure detection requires commitSpan() (or writing `status` directly).
            TraceSummaryRecord published = root(declared(dataSource), 701L);

            assertEquals(PUBLISH_OPERATION, published.rootName());
            assertEquals(0, published.errorCount(),
                    "no verdict was recorded, so none exists -- a code is not a statement of failure");
        }

        @Test
        @DisplayName("the declared template outranks a stale recorded name")
        void declaredTemplateOutranksARecordedName(DataSource dataSource) throws SQLException {
            assertEquals("PUBLISH payments", root(declared(dataSource), 703L).rootName(),
                    "the recorded name is one library version's answer; the declaration is the rule");
        }

        @Test
        @DisplayName("a recorded error is the verdict, on any commit path")
        void aRecordedErrorIsTheVerdict(DataSource dataSource) throws SQLException {
            // The one way a third-party event states a failure: record it. failed()-style direct
            // writes and commitSpan() both do; the derivation's ERROR escalation honours it.
            TraceSummaryRecord failed = root(declared(dataSource), 704L);

            assertEquals(1, failed.errorCount());
        }

        @Test
        @DisplayName("the identity template names a self-naming type by what it recorded")
        void identityTemplateReadsTheRecordedName(DataSource dataSource) throws SQLException {
            // The shape of a Jeffrey statement in a new recording: @Span("{name}") declared,
            // name recorded at construction. The declared arm and the recorded-name fallback must
            // agree -- the template exists for the invariant that every shipped span type declares
            // its convention, not to change any answer.
            TraceSummaryRecord statement = root(declared(dataSource), 708L);

            assertEquals("listSpans", statement.rootName());
        }

        @Test
        @DisplayName("the operations list carries the declared names, with no Jeffrey code involved")
        void operationsAreListedUnderDeclaredNames(DataSource dataSource) throws SQLException {
            Map<String, TraceOperationRecord> byName = declared(dataSource)
                    .operations(TraceOperationListQuery.busiest(100)).operations().stream()
                    .collect(Collectors.toMap(TraceOperationRecord::name, Function.identity()));

            TraceOperationRecord publish = byName.get(PUBLISH_OPERATION);
            assertNotNull(publish, "the zero-Jeffrey-changes proof: a foreign type, a real operation name");
            assertEquals(3, publish.count());
            assertEquals(1, publish.errorCount(), "only the recorded ERROR counts as a failure");
            assertTrue(byName.keySet().stream().noneMatch(name -> name.startsWith("com.acme.")),
                    "no declared type may fall back to its event type");
        }

        @Test
        @DisplayName("a profile with no declarations derives exactly as before")
        void noDeclarationsChangesNothing(DataSource dataSource) throws SQLException {
            // The existing fixtures keep extras NULL, so this pins that the declared-conventions
            // path renders to nothing rather than to broken SQL when a profile declares nothing.
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals("POST /api/internal/profiles/{profileId}/flamegraph",
                    repository.summaryOf(SLOW_TRACE).orElseThrow().rootName());
        }
    }

    @Nested
    @DisplayName("Contract with the event API")
    class EventApiContract {

        @Test
        @DisplayName("the kinds the derivation assigns are the kinds the instrumentation emits")
        void kindNamesMatchTheEnum() {
            assertEquals("SERVER", SpanKind.SERVER.name());
            assertEquals("CLIENT", SpanKind.CLIENT.name());
            assertEquals("INTERNAL", SpanKind.INTERNAL.name());
        }

        @Test
        @DisplayName("the SQL names an exchange the way the event names itself")
        void conventionsAgreeWithTheInstrumentation() {
            // One naming rule, three readers held together here: the built-in template (for
            // recordings that predate @Span), the annotation (carried in every new recording),
            // and describeSpan() (what commitSpan() writes into the raw event). @Inherited is
            // load-bearing for the annotation reads: the declarations live on the abstract bases
            // and must be readable off the concrete classes. The status assertions are about
            // describeSpan alone -- a verdict is only ever recorded, which is why commitSpan() is
            // the required path for failure detection.
            assertEquals(SpanNameTemplates.BUILT_IN.get(HttpServerExchangeEvent.NAME),
                    HttpServerExchangeEvent.class.getAnnotation(Span.class).value(),
                    "the built-in template IS the annotation's rule, one rule across vintages");
            assertEquals(SpanNameTemplates.BUILT_IN.get(GrpcServerExchangeEvent.NAME),
                    GrpcServerExchangeEvent.class.getAnnotation(Span.class).value());

            HttpServerExchangeEvent http = new HttpServerExchangeEvent();
            http.method = "GET";
            http.uri = "/api/internal/health";
            http.statusCode = 500;
            http.commitSpan();

            assertEquals("GET /api/internal/health", http.name,
                    "describeSpan must produce what the template declares");
            assertEquals(SpanKind.SERVER.name(), http.kind);
            assertEquals(SpanStatus.ERROR.name(), http.status,
                    "describeSpan records the verdict; nothing else does");

            GrpcServerExchangeEvent grpc = new GrpcServerExchangeEvent();
            grpc.service = "jeffrey.api.v1.ProjectService";
            grpc.method = "List";
            grpc.statusCode = "UNAVAILABLE";
            grpc.commitSpan();

            assertEquals("jeffrey.api.v1.ProjectService/List", grpc.name);
            assertEquals(SpanKind.SERVER.name(), grpc.kind);
            assertEquals(SpanStatus.ERROR.name(), grpc.status);
        }

        @Test
        @DisplayName("the event types the conventions name are the names the events register under")
        void eventTypeNamesMatchTheInstrumentation() {
            assertEquals(EventTypeName.HTTP_SERVER_EXCHANGE, HttpServerExchangeEvent.NAME);
            assertEquals(EventTypeName.HTTP_CLIENT_EXCHANGE, HttpClientExchangeEvent.NAME);
            assertEquals(EventTypeName.GRPC_SERVER_EXCHANGE, GrpcServerExchangeEvent.NAME);
            assertEquals(EventTypeName.GRPC_CLIENT_EXCHANGE, GrpcClientExchangeEvent.NAME);
        }

        @Test
        @DisplayName("the keys a declared convention travels under match the annotation")
        void conventionKeysMatchTheAnnotations() {
            // The parser matches the annotation by type name and the derivation reads extras by
            // key; neither module compiles against jeffrey-events, so these strings are the whole
            // contract. A rename on either side must fail here, not silently un-declare every
            // convention in every new recording.
            assertEquals(SpanConventionKeys.SPAN_ANNOTATION, Span.class.getName());
        }

        @Test
        @DisplayName("every span type the library ships declares its naming convention")
        void everySpanTypeDeclaresItsTemplate() {
            // Exchanges derive their name from their fields; a statement and a hand-written span
            // name themselves, which the identity template states explicitly. The invariant is
            // that no jeffrey-events span type is silent about how it is named -- absence of
            // @Span means "no convention exists", never "the rule lives elsewhere".
            assertEquals("{name}", JdbcQueryEvent.class.getAnnotation(Span.class).value());
            assertEquals("{name}", TraceSpanEvent.class.getAnnotation(Span.class).value());
        }

        @Test
        @DisplayName("the status the derivation writes is the status the instrumentation emits")
        void statusNamesMatchTheEnum() {
            // The derivation SQL spells these out as literals for readability. They are the same
            // values Tracer writes into the event, so a rename on either side has to be a rename on
            // both -- this is the guard that makes that visible rather than silent.
            assertEquals("OK", SpanStatus.OK.name());
            assertEquals("ERROR", SpanStatus.ERROR.name());
            assertEquals("UNSET", SpanStatus.UNSET.name());
        }
    }

    @Nested
    @DisplayName("Reads")
    class Reads {

        @Test
        @DisplayName("traces are listed slowest first, summarised over all their spans")
        void listsSlowestFirst(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSummaryRecord> traces = repository.traces(TraceListQuery.slowest(10)).traces();

            assertEquals(2, traces.size());
            TraceSummaryRecord slowest = traces.get(0);
            assertEquals(SLOW_TRACE, slowest.traceId());
            assertEquals("POST /api/internal/profiles/{profileId}/flamegraph", slowest.rootName());
            assertEquals("SERVER", slowest.rootKind());
            assertEquals(120 * MS, slowest.durationNanos());
            assertEquals(4, slowest.spanCount());
            assertEquals(1, slowest.errorCount());
            assertEquals(0, slowest.startMillisFromBeginning());

            assertEquals(FAST_TRACE, traces.get(1).traceId());
        }

        @Test
        @DisplayName("a trace reports whether any of its spans could carry samples")
        void reportsWhetherSamplesCanBeAttributed(DataSource dataSource) throws SQLException {
            Map<Long, TraceSummaryRecord> byTraceId = derived(dataSource).traces(TraceListQuery.slowest(10)).traces().stream()
                    .collect(Collectors.toMap(TraceSummaryRecord::traceId, Function.identity()));

            assertTrue(byTraceId.get(SLOW_TRACE).hasPlatformSpan(),
                    "its hand-written span ran on a platform thread");
            assertFalse(byTraceId.get(FAST_TRACE).hasPlatformSpan(),
                    "it never left the request's virtual thread");
        }

        @Test
        @DisplayName("the limit is honoured")
        void honoursLimit(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(1, repository.traces(TraceListQuery.slowest(1)).traces().size());
        }

        @Test
        @DisplayName("traces with the same duration are listed in a stable order")
        void tiedDurationsOrderDeterministically(DataSource dataSource) throws SQLException {
            // Ordering by duration alone leaves a tied row at the LIMIT boundary free to come and
            // go between two identical requests; trace_id breaks the tie.
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-tied-duration-traces.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            List<Long> ids = repository.traces(TraceListQuery.slowest(10)).traces().stream()
                    .map(TraceSummaryRecord::traceId)
                    .toList();

            assertEquals(List.of(SLOW_TRACE, 7001L, 7002L, FAST_TRACE), ids,
                    "the two 7ms traces fall back to trace_id order");
        }

        @Test
        @DisplayName("spans of a trace come back in start order")
        void spansAreOrdered(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);

            assertEquals(List.of(0L, 10L, 20L, 60L),
                    spans.stream().map(TraceSpanRecord::startMillisFromBeginning).toList());
            assertEquals(3001, spans.get(0).threadHash());
            assertEquals(3002, spans.get(3).threadHash(),
                    "the span committed on the pool thread keeps that thread's identity");
        }

        @Test
        @DisplayName("a span's start keeps the microseconds it was recorded with")
        void spanStartsAreMicrosecondResolution(DataSource dataSource) throws SQLException {
            // Flooring the start to a millisecond puts spans that ran within one of each other on
            // the same instant, and the waterfall then draws sequential work as if it overlapped.
            JdbcTraceRepository repository = derived(dataSource);

            TraceSpanRecord forked = repository.spansOf(SLOW_TRACE).stream()
                    .filter(span -> "flamegraph.generate".equals(span.name()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(EPOCH_10_00_00 * US_PER_MS + 60_750, forked.startEpochMicros());
        }

        @Test
        @DisplayName("operations aggregate traces by root name, ignoring nested spans")
        void aggregatesOperations(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceOperationRecord> byName = repository.operations(TraceOperationListQuery.busiest(10)).operations().stream()
                    .collect(Collectors.toMap(TraceOperationRecord::name, Function.identity()));

            assertEquals(2, byName.size(), "one row per trace type, not per span name");
            assertFalse(byName.containsKey("flamegraph.generate"),
                    "a nested span is not a trace type and must not be listed");
            assertFalse(byName.containsKey("listSpans"),
                    "a nested span is not a trace type and must not be listed");

            TraceOperationRecord slowest = byName.get("POST /api/internal/profiles/{profileId}/flamegraph");
            assertEquals(1, slowest.count(), "one trace of this type");
            assertEquals(4, slowest.spanCount(),
                    "the root, its two JDBC statements and the hand-written span");
            assertEquals(120 * MS, slowest.totalNanos(), "the whole trace, not the root span alone");
            assertEquals(120 * MS, slowest.maxNanos());
            assertEquals(1, slowest.errorCount(), "the trace contains a failed span");

            TraceOperationRecord health = byName.get("GET /api/internal/health");
            assertEquals(1, health.count());
            assertEquals(1, health.spanCount());
            assertEquals(5 * MS, health.totalNanos());
        }

        @Test
        @DisplayName("an operation reports the instrumentation that opened it")
        void reportsTheRootEventType(DataSource dataSource) throws SQLException {
            // The root's event type, not any nested span's: the slow trace's spans come from three
            // event types, and the one that matters is the type a trace of this kind starts at.
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceOperationRecord> byName = repository.operations(TraceOperationListQuery.busiest(10)).operations().stream()
                    .collect(Collectors.toMap(TraceOperationRecord::name, Function.identity()));

            assertEquals("jeffrey.HttpServerExchange",
                    byName.get("POST /api/internal/profiles/{profileId}/flamegraph").eventType());
            assertEquals("jeffrey.HttpServerExchange", byName.get("GET /api/internal/health").eventType());
        }

        @Test
        @DisplayName("the traces of an operation exclude other types and honour the limit")
        void listsTracesOfOneOperation(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSummaryRecord> traces = repository.tracesOfOperation(FLAMEGRAPH_OPERATION, 10);

            assertEquals(1, traces.size());
            assertEquals(SLOW_TRACE, traces.getFirst().traceId());
            assertEquals(120 * MS, traces.getFirst().durationNanos());
            assertEquals(4, traces.getFirst().spanCount());

            assertTrue(repository.tracesOfOperation(
                            operation("flamegraph.generate", "INTERNAL", "jeffrey.TraceSpan"), 10).isEmpty(),
                    "a nested span name roots no trace");
            assertTrue(repository.tracesOfOperation(HEALTH_OPERATION, 0).isEmpty(),
                    "a zero limit returns nothing rather than everything");
        }

        @Test
        @DisplayName("the overview totals the whole profile, counting failed traces and spans apart")
        void summarisesTheProfile(DataSource dataSource) throws SQLException {
            TraceOverviewRecord overview = derived(dataSource).overview();

            assertEquals(2, overview.totalTraces());
            assertEquals(5, overview.totalSpans());
            assertEquals(2, overview.errorTraces(), "both traces contain a failed span");
            assertEquals(2, overview.errorSpans(), "one failed span in each");
            assertEquals(2, overview.distinctOperations(), "distinct trace types, not span names");
            assertEquals(125 * MS, overview.totalNanos(), "120ms plus 5ms");
            assertEquals(120 * MS, overview.maxNanos());
            assertEquals(62_500_000L, overview.avgNanos(), "the mean of 120ms and 5ms");
            assertTrue(overview.avgNanos() <= overview.p95Nanos()
                            && overview.p95Nanos() <= overview.p99Nanos()
                            && overview.p99Nanos() <= overview.maxNanos(),
                    "percentiles sit between the mean and the slowest trace");
        }

        @Test
        @DisplayName("an untraced profile reports zeros, not the nulls its aggregates produce")
        void overviewOfAnUntracedProfileIsZeroed(DataSource dataSource) {
            JdbcTraceRepository empty = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            empty.derive();

            // SUM, MAX and QUANTILE_CONT over no rows are all SQL NULL, which getLong would flatten
            // to 0 silently -- asserted here so the COALESCEs cannot be dropped unnoticed.
            assertEquals(new TraceOverviewRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), empty.overview());
        }

        @Test
        @DisplayName("hasTraces gates the feature")
        void reportsWhetherTracesExist(DataSource dataSource) throws SQLException {
            JdbcTraceRepository empty = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            empty.derive();
            assertFalse(empty.hasTraces(), "a profile with no traced events has no traces");

            assertTrue(derived(dataSource).hasTraces());
        }

        @Test
        @DisplayName("the span breakdown ranks an operation's spans by total time, excluding its root")
        void breaksAnOperationDownBySpanName(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceOperationSpanRecord> spans =
                    repository.spanBreakdownOfOperation(FLAMEGRAPH_OPERATION, 10);

            // The root is what the operation *is*; repeating it as its own biggest child would say
            // nothing and would always top the list.
            assertTrue(spans.stream().noneMatch(span ->
                            "POST /api/internal/profiles/{profileId}/flamegraph".equals(span.name())),
                    "the root span is not part of its own breakdown");
            assertEquals(List.of("listSpans", "flamegraph.generate", "countSpans"),
                    spans.stream().map(TraceOperationSpanRecord::name).toList(),
                    "ranked by total time");

            TraceOperationSpanRecord slowest = spans.getFirst();
            assertEquals(1, slowest.occurrences());
            assertEquals(1, slowest.traceCount());
            assertEquals(40 * MS, slowest.totalNanos());
            assertEquals(40 * MS, slowest.maxNanos());
        }

        @Test
        @DisplayName("the thread split counts the spans a sample could be attributed to")
        void splitsAnOperationsSpansByThreadKind(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            TraceOperationThreadsRecord threads = repository.threadsOfOperation(FLAMEGRAPH_OPERATION);

            // Three spans on the virtual request thread, one forked onto the platform pool thread.
            assertEquals(2, threads.distinctThreads());
            assertEquals(1, threads.platformSpans());
            assertEquals(3, threads.virtualSpans());
        }

        @Test
        @DisplayName("an operation nobody ran summarises to nothing rather than failing")
        void unknownOperationIsEmpty(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            TraceOperationId unknown = operation("GET /nope", "SERVER", "jeffrey.HttpServerExchange");

            assertTrue(repository.spanBreakdownOfOperation(unknown, 10).isEmpty());
            assertEquals(TraceOperationThreadsRecord.EMPTY, repository.threadsOfOperation(unknown));
        }

        @Test
        @DisplayName("an unknown trace id yields no spans rather than failing")
        void unknownTraceIsEmpty(DataSource dataSource) throws SQLException {
            assertTrue(derived(dataSource).spansOf(42L).isEmpty());
        }

        @Test
        @DisplayName("a trace header reads the same whether it comes from a list or from its own id")
        void headerIsTheSameFromEitherRead(DataSource dataSource) throws SQLException {
            // The detail used to rebuild this from the spans, in microseconds, and so disagreed with
            // the list about the duration of the very trace the user had just clicked.
            JdbcTraceRepository repository = derived(dataSource);

            TraceSummaryRecord fromList = repository.traces(TraceListQuery.slowest(10)).traces().stream()
                    .filter(trace -> trace.traceId() == SLOW_TRACE).findFirst().orElseThrow();

            assertEquals(fromList, repository.summaryOf(SLOW_TRACE).orElseThrow());
        }

        @Test
        @DisplayName("an unknown trace id has no header rather than an empty one")
        void unknownTraceHasNoHeader(DataSource dataSource) throws SQLException {
            assertTrue(derived(dataSource).summaryOf(42L).isEmpty());
        }

        @Test
        @DisplayName("the drill-down shows JVM activity, not the spans themselves")
        void eventsInSpanExcludeSpans(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            // The window of the root HTTP span on thread 3001, which also carries a JDBC span and
            // an execution sample.
            ThreadWindowEventsPage page = repository.eventsInSpan(
                    3001, EPOCH_10_00_00, EPOCH_10_00_00 + 120);
            List<ThreadWindowEventRecord> events = page.events();

            assertTrue(events.stream().noneMatch(event -> event.eventType().startsWith("jeffrey.")),
                    "an event that is itself a span belongs in the waterfall, not inside a span");
            assertEquals(List.of("jdk.ExecutionSample"),
                    events.stream().map(ThreadWindowEventRecord::eventType).toList());
            assertFalse(page.truncated(), "a handful of events is nowhere near the row cap");
        }

        @Test
        @DisplayName("the drill-down is scoped to the span's own thread and window")
        void eventsInSpanAreScoped(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            // A different thread has no events of its own in the fixture.
            assertTrue(repository.eventsInSpan(3002, EPOCH_10_00_00, EPOCH_10_00_00 + 120).events().isEmpty());
            // A window before anything happened is empty too.
            assertTrue(repository.eventsInSpan(3001, EPOCH_10_00_00 - 500, EPOCH_10_00_00 - 1).events().isEmpty());
        }
    }

    @Nested
    @DisplayName("Self duration")
    class SelfDuration {

        /** The base fixture plus one trace per rule the self-time derivation has to obey. */
        private static Map<String, TraceSpanRecord> spansByName(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-self-duration-traces.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            return java.util.stream.LongStream.rangeClosed(8001, 8005)
                    .boxed()
                    .flatMap(traceId -> repository.spansOf(traceId).stream())
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));
        }

        @Test
        @DisplayName("is the span's duration minus what its children covered")
        void subtractsChildren(DataSource dataSource) throws SQLException {
            Map<String, TraceSpanRecord> spans = spansByName(dataSource);

            assertEquals(70 * MS, spans.get("sequential-parent").selfDurationNanos());
            assertEquals(30 * MS, spans.get("sequential-child").selfDurationNanos(),
                    "a leaf's self time is its whole duration");
        }

        @Test
        @DisplayName("overlapping children are not subtracted twice")
        void mergesOverlappingChildren(DataSource dataSource) throws SQLException {
            // The two children cover 10..50ms together, i.e. 40ms, not 30 + 30.
            assertEquals(60 * MS, spansByName(dataSource).get("overlap-parent").selfDurationNanos());
        }

        @Test
        @DisplayName("a child on another thread is not subtracted from the parent's own time")
        void ignoresChildrenOnOtherThreads(DataSource dataSource) throws SQLException {
            // Tracer.continueIn forks the work: the parent thread kept working the whole time.
            assertEquals(100 * MS, spansByName(dataSource).get("forking-parent").selfDurationNanos());
        }

        @Test
        @DisplayName("a child outliving its parent costs it only the stretch the two shared")
        void clipsChildrenToTheParentWindow(DataSource dataSource) throws SQLException {
            Map<String, TraceSpanRecord> spans = spansByName(dataSource);

            assertEquals(20 * MS, spans.get("overrun-parent").selfDurationNanos(),
                    "the child covered 20..50ms of the parent, not 20..100ms");
            assertEquals(80 * MS, spans.get("overrun-child").selfDurationNanos(),
                    "the child itself has no children, so all of its time is its own");
        }

        @Test
        @DisplayName("a child shorter than a millisecond still costs its parent that time")
        void subtractsSubMillisecondChildren(DataSource dataSource) throws SQLException {
            // Rounding each child's window to whole milliseconds made every sub-millisecond call
            // cost nothing, handing the parent back time its children had spent. A handful of short
            // queries under one request is the ordinary case, not a corner one.
            assertEquals(3_000 * US_PER_MS, spansByName(dataSource).get("submilli-parent").selfDurationNanos());
        }

        @Test
        @DisplayName("a span with no children keeps its whole duration")
        void leavesChildlessSpansAlone(DataSource dataSource) throws SQLException {
            Map<String, TraceSpanRecord> spans = spansByName(dataSource);

            for (String leaf : List.of("sequential-child", "overlap-a", "overlap-b", "forked-child")) {
                TraceSpanRecord span = spans.get(leaf);
                assertEquals(span.durationNanos(), span.selfDurationNanos(), leaf + " has no children");
            }
        }

        @Test
        @DisplayName("never exceeds the span's own duration")
        void neverExceedsTheDuration(DataSource dataSource) throws SQLException {
            for (TraceSpanRecord span : spansByName(dataSource).values()) {
                assertTrue(span.selfDurationNanos() >= 0, span.name() + " went negative");
                assertTrue(span.selfDurationNanos() <= span.durationNanos(),
                        span.name() + " claims more of its own time than it ran for");
            }
        }
    }

    @Nested
    @DisplayName("Filtering and paging")
    class Filtering {

        private static final String HEALTH = "GET /api/internal/health";
        private static final String FLAMEGRAPH = "POST /api/internal/profiles/{profileId}/flamegraph";

        private static TraceListQuery search(String term) {
            return new TraceListQuery(term, false, 0, null, TraceSortField.DURATION, true, 10, 0);
        }

        private static List<String> namesOf(TracePage page) {
            return page.traces().stream().map(TraceSummaryRecord::rootName).toList();
        }

        /** The base fixture plus one operation whose traces did not all end the same way. */
        private static JdbcTraceRepository withMixedOutcomes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-mixed-outcome-traces.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        @Test
        @DisplayName("a name filter matches anywhere in the name, ignoring case")
        void filtersByName(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(List.of(HEALTH), namesOf(repository.traces(search("health"))));
            assertEquals(List.of(HEALTH), namesOf(repository.traces(search("HEALTH"))),
                    "the search box is not case-sensitive");
            assertEquals(List.of(HEALTH), namesOf(repository.traces(search("internal/health"))),
                    "a substring matches mid-name, not just as a prefix");
        }

        @Test
        @DisplayName("a name filter treats wildcards as text")
        void escapesWildcardsInTheNameFilter(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertTrue(repository.traces(search("%")).traces().isEmpty(),
                    "a bare %% is a search term, not a pattern matching the whole table");
            assertTrue(repository.traces(search("health_")).traces().isEmpty(),
                    "the underscore is a character to find, not 'any character'");
            assertEquals(List.of(FLAMEGRAPH), namesOf(repository.traces(search("{profileId}"))),
                    "braces are ordinary characters, and operation names are full of them");
        }

        @Test
        @DisplayName("a blank name filter matches everything")
        void blankNameFilterMatchesEverything(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(2, repository.traces(search("")).traces().size());
            assertEquals(2, repository.traces(search("   ")).traces().size(),
                    "an empty search box never means 'names containing spaces'");
        }

        @Test
        @DisplayName("errors-only keeps the traces that failed")
        void filtersTracesByOutcome(DataSource dataSource) throws SQLException {
            // Both traces of the base fixture end in an error -- the health check is a 500 -- so the
            // successful traces this filter has to leave out come from the mixed-outcome fixture.
            JdbcTraceRepository repository = withMixedOutcomes(dataSource);
            TraceListQuery query =
                    new TraceListQuery(null, true, 0, null, TraceSortField.DURATION, true, 10, 0);

            TracePage page = repository.traces(query);

            assertEquals(3, page.totalMatching(), "3 of the 7 traces failed");
            assertTrue(page.traces().stream().allMatch(trace -> trace.errorCount() > 0));
            assertEquals(List.of(FLAMEGRAPH, "GET /mixed", HEALTH), namesOf(page));
        }

        @Test
        @DisplayName("a duration floor keeps the traces at least that long")
        void filtersByDuration(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            TraceListQuery query =
                    new TraceListQuery(null, false, 10 * MS, null, TraceSortField.DURATION, true, 10, 0);

            assertEquals(List.of(FLAMEGRAPH), namesOf(repository.traces(query)),
                    "the 5ms health trace is below the floor");
        }

        @Test
        @DisplayName("the total counts what the filter matched, not what the page returned")
        void reportsTotalMatchingBeyondThePage(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            TracePage page = repository.traces(TraceListQuery.slowest(1));

            assertEquals(1, page.traces().size(), "the page is capped");
            assertEquals(2, page.totalMatching(), "but the caller is told there is more");
        }

        @Test
        @DisplayName("the total narrows with the filter")
        void totalFollowsTheFilter(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(1, repository.traces(search("health")).totalMatching());
        }

        @Test
        @DisplayName("an offset pages past the rows already shown")
        void pagesByOffset(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            TraceListQuery secondPage =
                    new TraceListQuery(null, false, 0, null, TraceSortField.DURATION, true, 1, 1);

            TracePage page = repository.traces(secondPage);

            assertEquals(List.of(HEALTH), namesOf(page), "the slowest trace was on the first page");
            assertEquals(2, page.totalMatching(),
                    "the total is of the filter, so paging does not shrink it");
        }

        @Test
        @DisplayName("a filter matching nothing returns an empty page rather than a broken one")
        void emptyResultIsAPage(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            TracePage page = repository.traces(search("no-such-operation"));

            assertTrue(page.traces().isEmpty());
            assertEquals(0, page.totalMatching());
        }

        @Test
        @DisplayName("an operation filter narrows to exactly that trace type")
        void filtersByOperation(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            TraceOperationId health =
                    new TraceOperationId(HEALTH, "SERVER", "jeffrey.HttpServerExchange");
            TraceListQuery query =
                    new TraceListQuery(null, false, 0, health, TraceSortField.DURATION, true, 10, 0);

            TracePage page = repository.traces(query);

            assertEquals(List.of(HEALTH), namesOf(page));
            assertEquals(1, page.totalMatching(), "the total narrows with the operation");
        }

        @Test
        @DisplayName("an operation filter matches the whole triple, not the name alone")
        void operationFilterNeedsTheWholeTriple(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            // Right name, wrong kind: the same path as an outbound call would be, and must not match.
            TraceOperationId wrongKind =
                    new TraceOperationId(HEALTH, "CLIENT", "jeffrey.HttpServerExchange");
            TraceListQuery query = new TraceListQuery(
                    null, false, 0, wrongKind, TraceSortField.DURATION, true, 10, 0);

            TracePage page = repository.traces(query);

            assertTrue(page.traces().isEmpty());
            assertEquals(0, page.totalMatching());
        }

        @Test
        @DisplayName("the sort column and direction are honoured")
        void sortsAsAsked(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(List.of(HEALTH, FLAMEGRAPH), namesOf(repository.traces(
                            new TraceListQuery(null, false, 0, null, TraceSortField.DURATION, false, 10, 0))),
                    "ascending puts the fastest first");
            assertEquals(List.of(FLAMEGRAPH, HEALTH), namesOf(repository.traces(
                            new TraceListQuery(null, false, 0, null, TraceSortField.START, false, 10, 0))),
                    "the slow trace starts at the recording's origin");
        }

        @Test
        @DisplayName("operations can be searched by name")
        void filtersOperationsByName(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            TraceOperationListQuery query = new TraceOperationListQuery(
                    "health", false, TraceOperationSortField.TOTAL_TIME, true, 10, 0);

            assertEquals(List.of(HEALTH), repository.operations(query).operations().stream()
                    .map(TraceOperationRecord::name)
                    .toList());
            assertEquals(1, repository.operations(query).totalMatching());
        }

        @Test
        @DisplayName("errors-only lists the operations that failed without narrowing their aggregates")
        void operationErrorFilterKeepsTheWholePopulation(DataSource dataSource) throws SQLException {
            // The distinction a HAVING makes and a WHERE does not: `GET /mixed` succeeded twice and
            // failed once. Filtering the traces first would report it as a single 90ms trace.
            JdbcTraceRepository repository = withMixedOutcomes(dataSource);
            TraceOperationListQuery errorsOnly = new TraceOperationListQuery(
                    "mixed", true, TraceOperationSortField.TOTAL_TIME, true, 10, 0);

            TraceOperationRecord mixed = repository.operations(errorsOnly).operations().getFirst();

            assertEquals("GET /mixed", mixed.name());
            assertEquals(3, mixed.count(), "all three traces, not just the failed one");
            assertEquals(1, mixed.errorCount());
            assertEquals(120 * MS, mixed.totalNanos(), "10ms + 20ms + 90ms");
            assertEquals(90 * MS, mixed.maxNanos());
        }

        @Test
        @DisplayName("errors-only drops the operations that never failed")
        void operationErrorFilterDropsCleanOperations(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = withMixedOutcomes(dataSource);
            TraceOperationListQuery errorsOnly = new TraceOperationListQuery(
                    null, true, TraceOperationSortField.TOTAL_TIME, true, 10, 0);

            List<String> names = repository.operations(errorsOnly).operations().stream()
                    .map(TraceOperationRecord::name)
                    .toList();

            assertTrue(names.contains("GET /mixed"), "it failed once out of three");
            assertTrue(names.contains(FLAMEGRAPH));
            assertTrue(names.contains(HEALTH), "the fixture's health check answers 500");
            assertFalse(names.contains("GET /clean"), "this one never failed");
        }

        @Test
        @DisplayName("an operation reports a p99 over its whole population")
        void operationsCarryP99(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = withMixedOutcomes(dataSource);
            TraceOperationListQuery query = new TraceOperationListQuery(
                    "mixed", false, TraceOperationSortField.TOTAL_TIME, true, 10, 0);

            TraceOperationRecord mixed = repository.operations(query).operations().getFirst();

            assertTrue(mixed.p99Nanos() >= mixed.p95Nanos(),
                    "p99 cannot sit below p95 of the same population");
            assertTrue(mixed.p99Nanos() <= mixed.maxNanos(),
                    "nor above the slowest trace there was");
            assertEquals(20 * MS, mixed.p50Nanos(), "the middle of 10ms, 20ms and 90ms");
        }

        @Test
        @DisplayName("operations page the same way traces do")
        void pagesOperations(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);
            TraceOperationListQuery firstPage = new TraceOperationListQuery(
                    null, false, TraceOperationSortField.TOTAL_TIME, true, 1, 0);
            TraceOperationListQuery secondPage = new TraceOperationListQuery(
                    null, false, TraceOperationSortField.TOTAL_TIME, true, 1, 1);

            assertEquals(List.of(FLAMEGRAPH), repository.operations(firstPage).operations().stream()
                    .map(TraceOperationRecord::name).toList());
            assertEquals(List.of(HEALTH), repository.operations(secondPage).operations().stream()
                    .map(TraceOperationRecord::name).toList());
            assertEquals(2, repository.operations(secondPage).totalMatching());
        }
    }

    @Nested
    @DisplayName("JVM context")
    class Context {

        /** The span under test runs 10:10:00.000 .. 10:10:00.300, ten minutes into the fixture. */
        private static final long SPAN_FROM_US = (EPOCH_10_00_00 + 600_000L) * US_PER_MS;
        private static final long SPAN_TO_US = SPAN_FROM_US + 300L * US_PER_MS;
        private static final long CONTEXT_TRACE = 9001L;

        private static JdbcTraceRepository withContext(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
            TestUtils.executeSql(dataSource, "sql/events/insert-trace-context.sql");
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();
            return repository;
        }

        private static Map<TraceContextCategory, TraceSpanContextRecord> waitsByCategory(
                JdbcTraceRepository repository) {

            return repository.spanContext(CONTEXT_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanContextRecord::category, Function.identity()));
        }

        @Test
        @DisplayName("finds pauses recorded on a VM thread, not the span's own")
        void findsGlobalPauses(DataSource dataSource) throws SQLException {
            // The pauses live on thread 3003 while the span ran on 3001. A query that matched the
            // span's thread -- which is what every other window read in this layer does -- would
            // return nothing at all here.
            List<TracePauseRecord> pauses = withContext(dataSource).pausesInWindow(SPAN_FROM_US, SPAN_TO_US);

            assertEquals(2, pauses.size(), "the collection inside the span and the one overlapping its start");
            assertTrue(pauses.stream().allMatch(p -> p.category() == TraceContextCategory.GC_PAUSE));
        }

        @Test
        @DisplayName("includes a pause that began before the window and was still running")
        void includesOverlappingPause(DataSource dataSource) throws SQLException {
            // Starts-inside semantics miss this one: it began 30ms before the span and ran 10ms
            // into it. It is exactly the pause worth drawing, so it must survive the filter.
            List<TracePauseRecord> pauses = withContext(dataSource).pausesInWindow(SPAN_FROM_US, SPAN_TO_US);

            assertTrue(pauses.stream().anyMatch(p -> p.fromEpochMicros() < SPAN_FROM_US),
                    "a pause that started before the window still overlapped it");
        }

        @Test
        @DisplayName("excludes a pause that fell entirely outside the window")
        void excludesPausesOutsideTheWindow(DataSource dataSource) throws SQLException {
            List<TracePauseRecord> pauses = withContext(dataSource).pausesInWindow(SPAN_FROM_US, SPAN_TO_US);

            assertTrue(pauses.stream().noneMatch(p -> p.category() == TraceContextCategory.SAFEPOINT),
                    "the safepoint ran 200ms after the span ended");
        }

        @Test
        @DisplayName("a pause carries what it called itself")
        void pausesAreLabelled(DataSource dataSource) throws SQLException {
            List<TracePauseRecord> pauses = withContext(dataSource).pausesInWindow(SPAN_FROM_US, SPAN_TO_US);

            assertTrue(pauses.stream().allMatch(p -> "G1 Young".equals(p.label())),
                    "a band saying only 'something stopped the world' is not worth drawing");
        }

        @Test
        @DisplayName("a pause reports the window it occupied")
        void pausesCarryTheirBounds(DataSource dataSource) throws SQLException {
            TracePauseRecord inside = withContext(dataSource).pausesInWindow(SPAN_FROM_US, SPAN_TO_US).stream()
                    .filter(pause -> pause.fromEpochMicros() >= SPAN_FROM_US)
                    .findFirst()
                    .orElseThrow();

            assertEquals(100 * MS, inside.durationNanos());
            assertEquals(SPAN_FROM_US + 100 * US_PER_MS, inside.fromEpochMicros());
        }

        @Test
        @DisplayName("per-span waiting is grouped by what the thread was waiting on")
        void groupsSpanWaitsByCategory(DataSource dataSource) throws SQLException {
            Map<TraceContextCategory, TraceSpanContextRecord> waits = waitsByCategory(withContext(dataSource));

            assertEquals(30 * MS, waits.get(TraceContextCategory.MONITOR_BLOCKED).totalNanos());
            assertEquals(20 * MS, waits.get(TraceContextCategory.PARKED).totalNanos());
        }

        @Test
        @DisplayName("waiting on another thread is not attributed to the span")
        void ignoresOtherThreads(DataSource dataSource) throws SQLException {
            // A 90ms monitor wait on thread 3002 overlaps the span in time but not on its thread.
            Map<TraceContextCategory, TraceSpanContextRecord> waits = waitsByCategory(withContext(dataSource));

            assertEquals(30 * MS, waits.get(TraceContextCategory.MONITOR_BLOCKED).totalNanos(),
                    "only the wait on the span's own thread counts");
        }

        @Test
        @DisplayName("waiting after the span ended is not attributed to it")
        void ignoresEventsOutsideTheSpan(DataSource dataSource) throws SQLException {
            Map<TraceContextCategory, TraceSpanContextRecord> waits = waitsByCategory(withContext(dataSource));

            assertEquals(20 * MS, waits.get(TraceContextCategory.PARKED).totalNanos(),
                    "the 10ms park 600ms later is outside the span's window");
        }

        @Test
        @DisplayName("counts the events behind a total")
        void countsOccurrences(DataSource dataSource) throws SQLException {
            // One long stall and a thousand short ones sum the same and are not the same problem.
            Map<TraceContextCategory, TraceSpanContextRecord> waits = waitsByCategory(withContext(dataSource));

            assertEquals(1, waits.get(TraceContextCategory.MONITOR_BLOCKED).occurrences());
        }

        @Test
        @DisplayName("a trace whose spans never waited has no context rows")
        void quietTraceHasNoWaits(DataSource dataSource) throws SQLException {
            assertTrue(withContext(dataSource).spanContext(SLOW_TRACE).isEmpty(),
                    "a span that only ever ran gets no row rather than a row of zeroes");
        }

        @Test
        @DisplayName("a window with nothing in it comes back empty rather than failing")
        void emptyWindow(DataSource dataSource) throws SQLException {
            assertTrue(withContext(dataSource)
                    .pausesInWindow(SPAN_FROM_US - 10_000_000L, SPAN_FROM_US - 9_000_000L).isEmpty());
        }
    }

    @Nested
    @DisplayName("Timeline")
    class Timeline {

        @Test
        @DisplayName("buckets the traces over the stretch that holds them")
        void bucketsTraces(DataSource dataSource) throws SQLException {
            // The fixture's two traces start 5s apart, so any bucketing that separates them puts one
            // in each; the counts are what matters, not which bucket boundaries fell where.
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceTimelineBucketRecord> buckets = repository.timeline(10);

            assertEquals(2, buckets.stream().mapToLong(TraceTimelineBucketRecord::count).sum(),
                    "every trace lands in exactly one bucket");
            assertEquals(2, buckets.stream().mapToLong(TraceTimelineBucketRecord::errorCount).sum(),
                    "both fixture traces failed — the health check answers 500");
            assertEquals(120 * MS,
                    buckets.stream().mapToLong(TraceTimelineBucketRecord::maxDurationNanos).max().orElseThrow(),
                    "the slowest trace survives bucketing");
        }

        @Test
        @DisplayName("buckets come back in time order")
        void bucketsAreOrdered(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<Long> starts = repository.timeline(10).stream()
                    .map(TraceTimelineBucketRecord::fromMillisFromBeginning)
                    .toList();

            assertEquals(starts.stream().sorted().toList(), starts);
        }

        @Test
        @DisplayName("a single bucket holds the whole recording")
        void oneBucketHoldsEverything(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceTimelineBucketRecord> buckets = repository.timeline(1);

            assertEquals(1, buckets.size());
            assertEquals(2, buckets.getFirst().count());
        }

        @Test
        @DisplayName("a profile with no traces has no buckets")
        void untracedProfileHasNoBuckets(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            assertTrue(repository.timeline(10).isEmpty());
        }
    }
}
