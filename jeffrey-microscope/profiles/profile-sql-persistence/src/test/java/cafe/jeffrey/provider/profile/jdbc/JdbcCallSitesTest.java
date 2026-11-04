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
        private final Map<Long, Long> operationEvents = new HashMap<>();
        private final List<TraceOperationId> operations = new ArrayList<>();

        @Override
        public int pathOf(JfrStackTrace stackTrace) {
            stacks.add(stackTrace.id());
            return PATHS.get(stackTrace.id());
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
                new EventQueryConfigurer().withEventType(STATEMENT), groupField, new RecordingReader());
    }

    @Nested
    class Stacks {

        @Test
        void everyDistinctStackOfTheTypeIsChargedOnce(DataSource dataSource) throws SQLException {
            RecordingReader reader = read(dataSource, SQL_FIELD);

            assertEquals(Set.of(5001L, 5002L, 5003L, 5004L), Set.copyOf(reader.stacks));
            assertEquals(4, reader.stacks.size());
        }

        @Test
        void anEmptyReadAsksTheDatabaseNothingMore(DataSource dataSource) {
            RecordingReader reader = streamRepository(dataSource).callSites(
                    new EventQueryConfigurer().withEventType(STATEMENT), SQL_FIELD, new RecordingReader());

            assertTrue(reader.stacks.isEmpty());
            assertTrue(reader.sites.isEmpty());
            assertTrue(reader.operationEvents.isEmpty());
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
            assertEquals(3, reader.sites.size());
        }

        @Test
        void theUnattributedStacksArriveAsTheirOwnRowAndNoOtherTypeIsCounted(DataSource dataSource)
                throws SQLException {
            RecordingReader reader = read(dataSource, SQL_FIELD);

            assertEquals(1, reader.site(CallSiteReader.UNATTRIBUTED, FIND_ORDER).count());
            assertEquals(15, reader.sites.stream().mapToLong(CallSiteRow::count).sum());
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
