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
package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.CallSiteReader;
import cafe.jeffrey.provider.profile.api.CallSiteRow;
import cafe.jeffrey.provider.profile.api.CallSiteSelection;
import cafe.jeffrey.provider.profile.api.CallSiteTotals;
import cafe.jeffrey.provider.profile.api.CallSiteGroupRow;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.OperationStackRow;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The call-site read against DuckDB: the reader charges each distinct stack to a path, and the database
 * aggregates per path and grouping value, per trace included, over the stacks merged by path — what a
 * statement issued from one place through two stacks needs to be counted per request as one.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcCallSitesTest {

    private static final String FIXTURE = "sql/events/insert-call-sites.sql";
    private static final Type STATEMENT = Type.fromCode("jeffrey.JdbcStatement");
    private static final String SQL_FIELD = "sql";
    private static final String FIND_LINES = "SELECT order_lines";
    private static final String FIND_ORDER = "SELECT orders";
    private static final int LONG_STATEMENT_CHARS = 250;
    private static final TraceOperationId SHOW_ORDER = new TraceOperationId("GET /orders/{id}", "SERVER",
            "jeffrey.HttpServerExchange");

    /** Stacks 5001 and 5002 share path 0, 5003 is path 1, 5004 reaches no own code. */
    private static final Map<Long, Integer> PATHS = Map.of(
            5001L, 0, 5002L, 0, 5003L, 1, 5004L, CallSiteReader.UNATTRIBUTED);

    private static final class RecordingReader implements CallSiteReader<RecordingReader> {

        private final List<Long> stacks = new ArrayList<>();
        private final List<CallSiteRow> sites = new ArrayList<>();
        private final List<CallSiteGroupRow> groups = new ArrayList<>();
        private CallSiteTotals totals;
        private int totalsRead;
        private final Map<Long, Long> operationEvents = new HashMap<>();
        private final List<TraceOperationId> operations = new ArrayList<>();

        @Override
        public int pathOf(JfrStackTrace stackTrace) {
            stacks.add(stackTrace.id());
            return PATHS.get(stackTrace.id());
        }

        @Override
        public void onTotals(CallSiteTotals totals) {
            this.totals = totals;
            totalsRead++;
        }

        @Override
        public void onGroup(CallSiteGroupRow group) {
            groups.add(group);
        }

        @Override
        public void onSite(CallSiteRow row) {
            sites.add(row);
        }

        @Override
        public void onOperationStack(OperationStackRow row) {
            operationEvents.merge(row.stackTrace().id(), row.count(), Long::sum);
            operations.add(row.operation());
        }

        @Override
        public RecordingReader build() {
            return this;
        }

        CallSiteRow site(int pathId, String groupValue) {
            return sites.stream()
                    .filter(site -> site.pathId() == pathId)
                    .filter(site -> groupValue == null ? site.groupValue() == null : groupValue.equals(site.groupValue()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No site " + pathId + "/" + groupValue + " in " + sites));
        }

        CallSiteRow site(int pathId) {
            return sites.stream()
                    .filter(site -> site.pathId() == pathId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No site " + pathId + " in " + sites));
        }
    }

    private static ProfileEventStreamRepository streamRepository(DataSource dataSource) {
        QueryBuilderFactoryResolver resolver = new QueryBuilderFactoryResolverImpl(
                new DuckDBSQLFormatter(),
                new SimpleComplexQueries(
                        DuckDBFlamegraphQueries.of(), DuckDBTimeseriesQueries.of(), DuckDBSubSecondQueries.of()),
                new SimpleComplexQueries(
                        new DuckDBNativeFlamegraphQueries(),
                        new DuckDBNativeTimeseriesQueries(),
                        new DuckDBNativeSubSecondQueries()));
        return new JdbcProfileRepositories(new DuckDBSQLFormatter(), resolver, FrameResolutionMode.DATABASE)
                .newEventStreamRepository(dataSource);
    }

    private static RecordingReader read(DataSource dataSource, String groupField) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);
        return streamRepository(dataSource).callSites(
                new EventQueryConfigurer().withEventType(STATEMENT), groupField, new CallSiteSelection.Sites(50), new RecordingReader());
    }

    @Nested
    class BoundedResults {

        @Test
        void highCardinalityValuesAreBoundedBeforeTheyReachTheReader(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            addDistinctValues(dataSource, 5001);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new CallSiteSelection.Sites(50), new RecordingReader());

            assertEquals(50, reader.sites.size());
            assertEquals(new CallSiteTotals(115, 107320, 1, 103, 103), reader.totals);
            assertEquals(1, reader.totalsRead);
            assertEquals(FIND_LINES, reader.sites.getFirst().groupValue());
        }

        @Test
        void anExplicitSiteCapKeepsFullTotalsAndPerTraceFigures(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Sites(1), new RecordingReader());

            assertEquals(1, reader.sites.size());
            assertEquals(new CallSiteTotals(15, 2370, 1, 3, 3), reader.totals);
            assertEquals(FIND_LINES, reader.sites.getFirst().groupValue());
            assertEquals(5, reader.sites.getFirst().perTraceMax());
            assertEquals(4.0, reader.sites.getFirst().perTraceMedian());
            assertEquals(List.of(7L, 9L, 8L), reader.sites.getFirst().exemplarTraceIds());
        }

        @Test
        void groupCapsBoundBothGroupsAndTheirSites(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            addDistinctValues(dataSource, 5001);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Groups(2, 1), new RecordingReader());

            assertEquals(2, reader.groups.size());
            assertEquals(2, reader.sites.size());
            assertEquals(new CallSiteTotals(115, 107320, 1, 103, 103), reader.totals);
        }

        @Test
        void groupsRankByTheirCompleteCostAcrossEveryPath(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM events_raw");
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        VALUES
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.001Z', 500, 1, 5001, 1, '{"sql":"spread","traceId":7}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.002Z', 500, 1, 5003, 1, '{"sql":"spread","traceId":7}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.003Z', 900, 1, 5001, 1, '{"sql":"single","traceId":8}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.004Z', 100000, 1, 5004, 1, '{"sql":"unattributed"}')
                        """);
            }

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Groups(1, 1), new RecordingReader());

            assertEquals(new CallSiteTotals(4, 101900, 1, 3, 2), reader.totals);
            assertEquals(1, reader.groups.size());
            assertEquals("spread", reader.groups.getFirst().value());
            assertEquals(2, reader.groups.getFirst().count());
            assertEquals(1000, reader.groups.getFirst().totalNanos());
            assertEquals(2, reader.groups.getFirst().sites());
            assertEquals(1, reader.sites.size());
            assertEquals(500, reader.sites.getFirst().totalNanos());
        }

        @Test
        void equalDurationGroupsKeepTheStatementNamesOrder(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM events_raw");
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        VALUES
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.001Z', 900, 1, 5001, 1, '{"sql":"alpha"}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.002Z', 300, 1, 5001, 1, '{"sql":"zeta"}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.003Z', 300, 1, 5001, 1, '{"sql":"zeta"}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.004Z', 300, 1, 5001, 1, '{"sql":"zeta"}')
                        """);
            }

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Groups(1, 1), new RecordingReader());

            assertEquals("alpha", reader.groups.getFirst().value());
            assertEquals(900, reader.groups.getFirst().totalNanos());
        }

        @Test
        void valuesWithIdenticalTruncatedTextRemainDistinctGroups(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM events_raw");
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        SELECT 'jeffrey.JdbcStatement', '2025-01-15T10:00:00.001Z', 100, 1,
                               5001, 1, json_object('sql', repeat('S', 200) || suffix)
                        FROM (VALUES ('A'), ('B')) t(suffix)
                        """);
            }

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Groups(2, 1), new RecordingReader());

            assertEquals(new CallSiteTotals(2, 200, 0, 2, 2), reader.totals);
            assertEquals(2, reader.groups.size());
            assertEquals(1, reader.groups.stream().map(CallSiteGroupRow::value).distinct().count());
            assertEquals(2, reader.groups.stream().map(CallSiteGroupRow::key).distinct().count());
            assertTrue(reader.groups.stream().allMatch(group -> group.valueChars() == 201 && group.count() == 1));
        }

        @Test
        void unattributedGroupsDoNotConsumeSiteSlots(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            addDistinctValues(dataSource, 5004);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new CallSiteSelection.Sites(50), new RecordingReader());

            assertEquals(3, reader.sites.size());
            assertTrue(reader.sites.stream().noneMatch(site -> site.pathId() == CallSiteReader.UNATTRIBUTED));
            assertEquals(new CallSiteTotals(115, 107320, 101, 3, 3), reader.totals);
        }

        private static void addDistinctValues(DataSource dataSource, long stackHash) throws SQLException {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        SELECT 'jeffrey.JdbcStatement', '2025-01-15T10:00:00.008Z', 1000 + i, 1,
                               %d, 1, json_object('sql', 'dynamic-' || i)
                        FROM range(100) t(i)
                        """.formatted(stackHash));
            }
        }
    }

    /** Numbers paths in the order stacks arrive, as the real reader does, so the order reaches the ranking. */
    private static final class ArrivalOrderReader implements CallSiteReader<ArrivalOrderReader> {

        private final List<Long> stacks = new ArrayList<>();
        private final List<CallSiteRow> sites = new ArrayList<>();

        @Override
        public int pathOf(JfrStackTrace stackTrace) {
            stacks.add(stackTrace.id());
            return stacks.size() - 1;
        }

        @Override
        public void onTotals(CallSiteTotals totals) {
        }

        @Override
        public void onGroup(CallSiteGroupRow group) {
        }

        @Override
        public void onSite(CallSiteRow row) {
            sites.add(row);
        }

        @Override
        public void onOperationStack(OperationStackRow row) {
        }

        @Override
        public ArrivalOrderReader build() {
            return this;
        }

        long stackOfFirstSite() {
            return stacks.get(sites.getFirst().pathId());
        }
    }

    @Nested
    class TiesAtTheCap {

        /** Two sites alike in duration and count, written newest stack first. */
        private static void insertTiedSites(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM events_raw");
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        VALUES
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.001Z', 100, 1, 5003, 1, '{"sql":"tied"}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.002Z', 100, 1, 5002, 1, '{"sql":"tied"}'),
                        ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.003Z', 100, 1, 5001, 1, '{"sql":"tied"}')
                        """);
            }
        }

        @Test
        void stacksArriveInHashOrder(DataSource dataSource) throws SQLException {
            insertTiedSites(dataSource);

            ArrivalOrderReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD,
                    new CallSiteSelection.Sites(1), new ArrivalOrderReader());

            assertEquals(List.of(5001L, 5002L, 5003L), reader.stacks);
        }

        @Test
        void aCapBreaksATieByTheLowestStackEveryTime(DataSource dataSource) throws SQLException {
            insertTiedSites(dataSource);

            for (CallSiteSelection selection : List.of(new CallSiteSelection.Sites(1), new CallSiteSelection.Groups(1, 1))) {
                ArrivalOrderReader reader = streamRepository(dataSource).callSites(
                        new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, selection,
                        new ArrivalOrderReader());

                assertEquals(1, reader.sites.size(), selection.toString());
                assertEquals(5001L, reader.stackOfFirstSite(), selection.toString());
            }
        }
    }

    @Nested
    class Stacks {

        @Test
        void eventsWithoutStacksRemainInTheTotals(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            insertStacklessEvent(dataSource);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new CallSiteSelection.Sites(50), new RecordingReader());

            assertEquals(16, reader.totals.totalEvents());
            assertEquals(2470, reader.totals.totalNanos());
            assertEquals(2, reader.totals.unattributedEvents());
        }

        @Test
        void aReadWithOnlyStacklessEventsStillCountsThem(DataSource dataSource) throws SQLException {
            insertStacklessEvent(dataSource);

            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new CallSiteSelection.Sites(50), new RecordingReader());

            assertTrue(reader.stacks.isEmpty());
            assertTrue(reader.sites.isEmpty());
            assertEquals(1, reader.totals.totalEvents());
            assertEquals(1, reader.totals.unattributedEvents());
            assertEquals(100, reader.totals.totalNanos());
            assertTrue(reader.operationEvents.isEmpty());
        }

        private static void insertStacklessEvent(DataSource dataSource) throws SQLException {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO events_raw (event_type, start_timestamp, duration, samples,
                                                stacktrace_hash, thread_hash, fields)
                        VALUES ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.008Z', 100, 1, NULL, 1, '{}')
                        """);
            }
        }

        @Test
        void everyDistinctStackOfTheTypeIsChargedOnce(DataSource dataSource) throws SQLException {
            RecordingReader reader = read(dataSource, SQL_FIELD);

            assertEquals(Set.of(5001L, 5002L, 5003L, 5004L), Set.copyOf(reader.stacks));
            assertEquals(4, reader.stacks.size());
        }

        @Test
        void anEmptyReadHasZeroTotalsAndNoSites(DataSource dataSource) {
            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new CallSiteSelection.Sites(50), new RecordingReader());

            assertTrue(reader.stacks.isEmpty());
            assertTrue(reader.sites.isEmpty());
            assertTrue(reader.operationEvents.isEmpty());
            assertEquals(CallSiteTotals.EMPTY, reader.totals);
        }
    }

    @Nested
    class PerTrace {

        @Test
        void countsOneRequestAcrossEveryStackOfThePath(DataSource dataSource) throws SQLException {
            CallSiteRow site = read(dataSource, SQL_FIELD).site(0, FIND_LINES);

            assertEquals(11, site.count());
            assertEquals(1_900, site.totalNanos());
            assertEquals(900, site.maxNanos());
            assertEquals(3, site.traces());
            assertEquals(5, site.perTraceMax());
            assertEquals(4.0, site.perTraceMedian());
            assertEquals(List.of(7L, 9L, 8L), site.exemplarTraceIds());
        }

        @Test
        void eventsOutsideTracesHaveNoPerTraceFigures(DataSource dataSource) throws SQLException {
            CallSiteRow site = read(dataSource, SQL_FIELD).site(0, FIND_ORDER);

            assertEquals(2, site.count());
            assertEquals(0, site.traces());
            assertEquals(0, site.perTraceMax());
            assertEquals(0.0, site.perTraceMedian());
            assertTrue(site.exemplarTraceIds().isEmpty());
        }
    }

    @Nested
    class Grouping {

        @Test
        void aPooledValueIsReadWholeAndHandedOverCut(DataSource dataSource) throws SQLException {
            CallSiteRow site = read(dataSource, SQL_FIELD).site(1);

            assertEquals(CallSiteRow.MAX_GROUP_VALUE_CHARS, site.groupValue().length());
            assertEquals(LONG_STATEMENT_CHARS, site.groupValueChars());
            assertEquals(32, site.groupKey().length());
            assertEquals(List.of(7L), site.exemplarTraceIds());
        }

        @Test
        void withoutAFieldEachPathIsOneRow(DataSource dataSource) throws SQLException {
            RecordingReader reader = read(dataSource, null);

            CallSiteRow site = reader.site(0);
            assertNull(site.groupValue());
            assertNull(site.groupValueChars());
            assertEquals(13, site.count());
            assertEquals(3, site.traces());
            assertEquals(2, reader.sites.size());
            assertEquals(1, reader.groups.size());
            assertNull(reader.groups.getFirst().key());
        }

        @Test
        void unattributedStacksRemainInTotalsAndNoOtherTypeIsCounted(DataSource dataSource)
                throws SQLException {
            RecordingReader reader = read(dataSource, SQL_FIELD);

            assertEquals(1, reader.totals.unattributedEvents());
            assertEquals(15, reader.totals.totalEvents());
            assertEquals(14, reader.sites.stream().mapToLong(CallSiteRow::count).sum());
        }
    }

    @Nested
    class Operations {

        @Test
        void eachStacksEventsInTracesPerOperation(DataSource dataSource) throws SQLException {
            RecordingReader reader = read(dataSource, SQL_FIELD);

            assertEquals(Map.of(5001L, 5L, 5002L, 6L, 5003L, 1L, 5004L, 1L), reader.operationEvents);
            assertEquals(Set.of(SHOW_ORDER), Set.copyOf(reader.operations));
        }
    }
}
