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

import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.provider.profile.api.TraceEventRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
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
    private static final long SLOW_TRACE = Long.MAX_VALUE;
    private static final long FAST_TRACE = Long.MIN_VALUE;
    private static final long NEGATIVE_SPAN_ID = -8113938001533374712L;
    /** The HTTP exchange the whole slow trace hangs off, and the span both statements are stamped with. */
    private static final long ROOT_SPAN_ID = 111L;
    /** 2025-01-15T10:00:00Z, the fixture's origin, as epoch millis. */
    private static final long EPOCH_10_00_00 = 1736935200000L;

    private static JdbcTraceRepository derived(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
        JdbcTraceRepository repository = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
        repository.derive();
        return repository;
    }

    @Nested
    @DisplayName("Derivation")
    class Derivation {

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
        @DisplayName("a stamped event becomes a span of its own, under the span it was stamped with")
        void givesStampedEventsTheirOwnIdentity(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);
            Map<String, TraceSpanRecord> byName = spans.stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            // Both statements were stamped with span 111 -- the exchange's own id, not theirs. A
            // span id has to identify one span, so each statement is given one and hangs off 111.
            assertEquals(spans.size(), spans.stream().map(TraceSpanRecord::spanId).distinct().count(),
                    "every derived span needs an id of its own");
            assertEquals(ROOT_SPAN_ID, byName.get("listSpans").parentSpanId());
            assertEquals(ROOT_SPAN_ID, byName.get("countSpans").parentSpanId());
            assertTrue(spans.stream()
                            .filter(span -> span.eventType().startsWith("jeffrey.Jdbc"))
                            .noneMatch(span -> span.spanId() == ROOT_SPAN_ID),
                    "a stamped event must not claim the id of the span that was in progress");
        }

        @Test
        @DisplayName("an event that owns its span keeps the id it recorded")
        void keepsRecordedIdsOfSpanOwningEvents(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            assertEquals(ROOT_SPAN_ID, byName.get("POST /api/internal/profiles/{profileId}/flamegraph").spanId());
            assertEquals(NEGATIVE_SPAN_ID, byName.get("flamegraph.generate").spanId());
        }

        @Test
        @DisplayName("round-trips 64-bit ids through JSON without losing precision or sign")
        void preservesIdBoundaries(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSpanRecord> spans = repository.spansOf(SLOW_TRACE);

            assertTrue(spans.stream().allMatch(span -> span.traceId() == Long.MAX_VALUE));
            assertTrue(spans.stream().anyMatch(span -> span.spanId() == NEGATIVE_SPAN_ID),
                    "a negative span id must survive the JSON round trip");
            assertFalse(repository.slowestTraces(10).stream()
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
        @DisplayName("names and kinds are derived from whichever event produced the span")
        void derivesNameAndKindPerEventType(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            // An HTTP exchange is named by method + matched template, and is a SERVER span.
            assertEquals("SERVER", byName.get("POST /api/internal/profiles/{profileId}/flamegraph").kind());
            // A JDBC statement takes its statement name and is a CLIENT span.
            assertEquals("CLIENT", byName.get("listSpans").kind());
            // A hand-written span names and classifies itself.
            assertEquals("INTERNAL", byName.get("flamegraph.generate").kind());
        }

        @Test
        @DisplayName("failure is recognised per event type")
        void derivesStatus(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceSpanRecord> byName = repository.spansOf(SLOW_TRACE).stream()
                    .collect(Collectors.toMap(TraceSpanRecord::name, Function.identity()));

            TraceSpanRecord failed = byName.get("flamegraph.generate");
            assertEquals("ERROR", failed.status());
            assertEquals("java.lang.IllegalStateException", failed.errorType());
            assertEquals("UNSET", byName.get("listSpans").status());

            // HTTP 500 is an error even though the exchange itself carries no status field.
            TraceSummaryRecord fast = repository.slowestTraces(10).stream()
                    .filter(trace -> trace.traceId() == FAST_TRACE).findFirst().orElseThrow();
            assertEquals(1, fast.errorCount());
        }

        @Test
        @DisplayName("the originating event's fields are kept as the span's attributes")
        void keepsAttributes(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            TraceSpanRecord jdbc = repository.spansOf(SLOW_TRACE).stream()
                    .filter(span -> "listSpans".equals(span.name())).findFirst().orElseThrow();

            assertNotNull(jdbc.attributes());
            assertTrue(jdbc.attributes().contains("PROFILE_EVENTS"),
                    "the span should carry everything its source event knew");
        }
    }

    @Nested
    @DisplayName("Contract with the event API")
    class EventApiContract {

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

        @Test
        @DisplayName("the kinds the derivation assigns are the kinds the instrumentation emits")
        void kindNamesMatchTheEnum() {
            assertEquals("SERVER", SpanKind.SERVER.name());
            assertEquals("CLIENT", SpanKind.CLIENT.name());
            assertEquals("INTERNAL", SpanKind.INTERNAL.name());
        }
    }

    @Nested
    @DisplayName("Reads")
    class Reads {

        @Test
        @DisplayName("traces are listed slowest first, summarised over all their spans")
        void listsSlowestFirst(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSummaryRecord> traces = repository.slowestTraces(10);

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
        @DisplayName("the limit is honoured")
        void honoursLimit(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            assertEquals(1, repository.slowestTraces(1).size());
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
        @DisplayName("operations aggregate latency by name across traces")
        void aggregatesOperations(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceOperationRecord> byName = repository.operations(10).stream()
                    .collect(Collectors.toMap(TraceOperationRecord::name, Function.identity()));

            TraceOperationRecord slowest = byName.get("POST /api/internal/profiles/{profileId}/flamegraph");
            assertEquals(1, slowest.count());
            assertEquals(120 * MS, slowest.totalNanos());
            assertEquals(120 * MS, slowest.maxNanos());

            assertEquals(1, byName.get("flamegraph.generate").errorCount());
        }

        @Test
        @DisplayName("the overview totals the whole profile, counting failed traces and spans apart")
        void summarisesTheProfile(DataSource dataSource) throws SQLException {
            TraceOverviewRecord overview = derived(dataSource).overview();

            assertEquals(2, overview.totalTraces());
            assertEquals(5, overview.totalSpans());
            assertEquals(2, overview.errorTraces(), "both traces contain a failed span");
            assertEquals(2, overview.errorSpans(), "one failed span in each");
            assertEquals(5, overview.distinctOperations(), "distinct span names, not root names");
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
            assertEquals(new TraceOverviewRecord(0, 0, 0, 0, 0, 0, 0, 0, 0), empty.overview());
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
        @DisplayName("an unknown trace id yields no spans rather than failing")
        void unknownTraceIsEmpty(DataSource dataSource) throws SQLException {
            assertTrue(derived(dataSource).spansOf(42L).isEmpty());
        }

        @Test
        @DisplayName("the drill-down shows JVM activity, not the spans themselves")
        void eventsInSpanExcludeSpans(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            // The window of the root HTTP span on thread 3001, which also carries a JDBC span and
            // an execution sample.
            List<TraceEventRecord> events = repository.eventsInSpan(
                    3001, EPOCH_10_00_00, EPOCH_10_00_00 + 120);

            assertTrue(events.stream().noneMatch(event -> event.eventType().startsWith("jeffrey.")),
                    "an event that is itself a span belongs in the waterfall, not inside a span");
            assertEquals(List.of("jdk.ExecutionSample"),
                    events.stream().map(TraceEventRecord::eventType).toList());
        }

        @Test
        @DisplayName("the drill-down is scoped to the span's own thread and window")
        void eventsInSpanAreScoped(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            // A different thread has no events of its own in the fixture.
            assertTrue(repository.eventsInSpan(3002, EPOCH_10_00_00, EPOCH_10_00_00 + 120).isEmpty());
            // A window before anything happened is empty too.
            assertTrue(repository.eventsInSpan(3001, EPOCH_10_00_00 - 500, EPOCH_10_00_00 - 1).isEmpty());
        }
    }
}
