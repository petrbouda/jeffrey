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

import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueKind;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.provider.profile.api.TraceSpanTypeRecord;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the attribute index over the same fixture the trace tests use — one HTTP request that issued
 * two statements and ran a hand-written span with an attribute map of its own, plus a second,
 * faster request that failed.
 * <p>
 * That fixture is what makes the sharpest case here testable at all: the attribute map lives on one
 * span and the statement's row count on another, so a search for both together has one answer per
 * scope, and they are different answers.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcTraceAttributeRepositoryTest {

    private static final String HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange";
    private static final String JDBC_QUERY = "jeffrey.JdbcQuery";
    private static final String TRACE_SPAN = "jeffrey.TraceSpan";

    /** The map the hand-written span attached: {@code {"eventType":"jdk.ExecutionSample","depth":7}}. */
    private static final TraceAttributeKeyId ATTRIBUTE_DEPTH = TraceAttributeKeyId.attribute("depth");
    private static final TraceAttributeKeyId ATTRIBUTE_EVENT_TYPE =
            TraceAttributeKeyId.attribute("eventType");
    /** The same name as the attribute above, declared by the span shape rather than attached. */
    private static final TraceAttributeKeyId SHAPE_EVENT_TYPE =
            new TraceAttributeKeyId(TraceAttributeSource.SPAN_SHAPE, null, "eventType");
    private static final TraceAttributeKeyId JDBC_ROWS =
            new TraceAttributeKeyId(TraceAttributeSource.EVENT_FIELD, JDBC_QUERY, "rows");
    private static final TraceAttributeKeyId HTTP_STATUS_CODE =
            new TraceAttributeKeyId(TraceAttributeSource.EVENT_FIELD, HTTP_SERVER_EXCHANGE, "statusCode");

    /** Both traces of the fixture: the slow one, and the fast one that failed. */
    private static final int TRACES = 2;

    private static JdbcTraceAttributeRepository derived(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");

        DatabaseClientProvider provider = new DatabaseClientProvider(dataSource);
        // The attribute index is derived from trace_spans, so the trace derivation runs first --
        // the same order ProfileInitializerImpl uses.
        new JdbcTraceRepository(provider).derive();

        JdbcTraceAttributeRepository repository = new JdbcTraceAttributeRepository(provider);
        repository.derive();
        return repository;
    }

    private static Optional<TraceAttributeKeyRecord> keyOf(
            JdbcTraceAttributeRepository repository, TraceAttributeKeyId id) {

        return repository.keys().stream()
                .filter(key -> key.id().equals(id))
                .findFirst();
    }

    private static TraceAttributeSearchQuery search(
            TraceAttributeScope scope, TraceAttributeCondition... conditions) {

        return new TraceAttributeSearchQuery(
                List.of(conditions), scope, TraceSortField.DURATION, true, 50, 0);
    }

    private static TraceAttributeCondition holds(TraceAttributeKeyId key, String value) {
        return new TraceAttributeCondition(key, TraceAttributeOperator.EQ, value);
    }

    @Nested
    @DisplayName("Derivation")
    class Derivation {

        @Test
        @DisplayName("the attribute map becomes one key per entry")
        void attributeMapIsFlattened(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            List<TraceAttributeKeyId> attributes = repository.keys().stream()
                    .map(TraceAttributeKeyRecord::id)
                    .filter(id -> id.source() == TraceAttributeSource.ATTRIBUTE)
                    .toList();

            assertEquals(2, attributes.size(), "the map has exactly two entries");
            assertTrue(attributes.contains(ATTRIBUTE_DEPTH));
            assertTrue(attributes.contains(ATTRIBUTE_EVENT_TYPE));
        }

        @Test
        @DisplayName("an event type's declared fields are qualified by the type declaring them")
        void eventFieldsCarryTheirOwner(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            assertTrue(keyOf(repository, JDBC_ROWS).isPresent());
            assertTrue(keyOf(repository, HTTP_STATUS_CODE).isPresent());
            assertTrue(
                    keyOf(repository, new TraceAttributeKeyId(
                            TraceAttributeSource.EVENT_FIELD, TRACE_SPAN, "rows")).isEmpty(),
                    "a hand-written span declares nothing of its own, so it owns no field");
        }

        @Test
        @DisplayName("a key attached by hand and a span column of the same name stay apart")
        void sameNameDifferentSource(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            TraceAttributeKeyRecord attached = keyOf(repository, ATTRIBUTE_EVENT_TYPE).orElseThrow();
            TraceAttributeKeyRecord column = keyOf(repository, SHAPE_EVENT_TYPE).orElseThrow();

            // Merging the two would report the profile as having four kinds of event type, and would
            // make a search for one of them return the other's spans.
            assertEquals(1, attached.distinctValues(), "only the hand-written span attached one");
            assertEquals(3, column.distinctValues(), "the exchange, the statements and the span");
        }

        @Test
        @DisplayName("the kind of a key's values is inferred from the values")
        void valueKindIsInferred(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            assertEquals(TraceAttributeValueKind.NUMBER,
                    keyOf(repository, ATTRIBUTE_DEPTH).orElseThrow().valueKind());
            assertEquals(TraceAttributeValueKind.STRING,
                    keyOf(repository, ATTRIBUTE_EVENT_TYPE).orElseThrow().valueKind());
        }

        @Test
        @DisplayName("deriving twice lands where deriving once did")
        void derivationIsIdempotent(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);
            List<TraceAttributeKeyRecord> once = repository.keys();

            repository.derive();

            assertEquals(once, repository.keys(),
                    "a re-derived profile that doubled every row would report twice the coverage");
        }

        @Test
        @DisplayName("a profile with no traced event derives an empty index")
        void untracedProfileStaysEmpty(DataSource dataSource) {
            JdbcTraceAttributeRepository repository =
                    new JdbcTraceAttributeRepository(new DatabaseClientProvider(dataSource));
            repository.derive();

            assertTrue(repository.keys().isEmpty());
        }
    }

    @Nested
    @DisplayName("Search")
    class Search {

        @Test
        @DisplayName("a condition narrows to the traces carrying it")
        void oneCondition(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derived(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(ATTRIBUTE_DEPTH, "7")));

            assertEquals(1, page.total());
            assertEquals(1, page.traces().size());
            assertEquals(1, page.stats().traces());
        }

        @Test
        @DisplayName("no condition matches every trace, like the unfiltered list")
        void noConditions(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page =
                    derived(dataSource).search(search(TraceAttributeScope.TRACE));

            assertEquals(TRACES, page.total());
            assertTrue(page.hits().isEmpty(), "nothing was asked for, so no span matched anything");
        }

        /**
         * The distinction the whole scope toggle exists for. The attribute map is on the hand-written
         * span and the row count is on a statement, so the two conditions hold in the same trace and
         * never on the same span.
         */
        @Test
        @DisplayName("conditions on different spans match the trace but not one span")
        void scopeDecidesTheAnswer(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            TraceAttributeCondition attached = holds(ATTRIBUTE_EVENT_TYPE, "jdk.ExecutionSample");
            TraceAttributeCondition rows = holds(JDBC_ROWS, "42");

            assertEquals(1, repository.search(
                    search(TraceAttributeScope.TRACE, attached, rows)).total());
            assertEquals(0, repository.search(
                    search(TraceAttributeScope.SPAN, attached, rows)).total(),
                    "no single span carries both, and saying otherwise is the bug this scope prevents");
        }

        @Test
        @DisplayName("the matching spans come back with the page")
        void hitsNameTheSpanThatMatched(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derived(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(JDBC_ROWS, "42")));

            assertEquals(1, page.hits().size());
            TraceAttributeRepository.Hit hit = page.hits().getFirst();
            assertEquals("rows", hit.key());
            assertEquals("42", hit.value());
            assertEquals(page.traces().getFirst().traceId(), hit.traceId());
        }

        @Test
        @DisplayName("a comparison reads the numeric column rather than the text")
        void numericComparison(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            // 200 and 500 as text would order "200" before "500" too, so the fixture cannot tell the
            // two apart that way -- but 42 > 9 can only hold numerically.
            assertEquals(1, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(JDBC_ROWS, TraceAttributeOperator.GT, "9"))).total());
            assertEquals(0, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(JDBC_ROWS, TraceAttributeOperator.GT, "99"))).total());
        }

        @Test
        @DisplayName("a value nothing carries matches nothing, and says so with empty stats")
        void noMatch(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derived(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(ATTRIBUTE_DEPTH, "999")));

            assertEquals(0, page.total());
            assertTrue(page.traces().isEmpty());
            assertEquals(0, page.stats().traces());
        }

        @Test
        @DisplayName("the timeline counts the matches against every trace")
        void timelineCarriesBothCounts(DataSource dataSource) throws SQLException {
            List<TraceAttributeRepository.TimelineBucket> buckets = derived(dataSource)
                    .timeline(search(TraceAttributeScope.TRACE, holds(ATTRIBUTE_DEPTH, "7")), 10);

            assertFalse(buckets.isEmpty());
            long matched = buckets.stream().mapToLong(TraceAttributeRepository.TimelineBucket::matched).sum();
            long total = buckets.stream().mapToLong(TraceAttributeRepository.TimelineBucket::total).sum();

            assertEquals(1, matched);
            assertEquals(TRACES, total, "the backdrop is the profile, not the match");
        }
    }

    @Nested
    @DisplayName("Values")
    class Values {

        @Test
        @DisplayName("each value carries what the traces holding it cost")
        void valuesAreSummarised(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.Values values = derived(dataSource).values(
                    new TraceAttributeValueQuery(
                            HTTP_STATUS_CODE, TraceAttributeValueSortField.TOTAL_TIME, true, 50, null));

            assertEquals(2, values.values().size(), "200 and 500");
            TraceAttributeValueRecord slowest = values.values().getFirst();
            assertEquals("200", slowest.value(), "the successful request is the slow one here");
            assertEquals(1, slowest.traceCount());
            assertTrue(slowest.totalNanos() > 0);
        }

        /**
         * The row a breakdown is not allowed to leave out. Half the profile never recorded this key,
         * and a table that shows only the values makes every share on it a share of the wrong total.
         */
        @Test
        @DisplayName("traces carrying the key nowhere are counted and reported")
        void absentTracesAreReported(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.Values values = derived(dataSource).values(
                    new TraceAttributeValueQuery(
                            ATTRIBUTE_DEPTH, TraceAttributeValueSortField.TOTAL_TIME, true, 50, null));

            assertEquals(1, values.values().size());
            assertEquals(1, values.tracesWithoutKey(), "the second trace has no such attribute");
        }

        @Test
        @DisplayName("a key nothing recorded breaks down to nothing")
        void unknownKey(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.Values values = derived(dataSource).values(
                    new TraceAttributeValueQuery(
                            TraceAttributeKeyId.attribute("nope"),
                            TraceAttributeValueSortField.TOTAL_TIME, true, 50, null));

            assertEquals(TraceAttributeRepository.Values.EMPTY, values);
        }
    }

    @Nested
    @DisplayName("Event types")
    class EventTypes {

        /** The cap the manager applies; high enough here that nothing in the fixture is search-only. */
        private static final long NO_CAP = 1_000;

        @Test
        @DisplayName("every event type that produced spans is listed, busiest first")
        void spanEventTypesAreListed(DataSource dataSource) throws SQLException {
            List<TraceSpanTypeRecord> types = derived(dataSource).spanEventTypes(NO_CAP);

            assertFalse(types.isEmpty());
            assertTrue(
                    types.stream().map(TraceSpanTypeRecord::eventType).toList()
                            .containsAll(List.of(HTTP_SERVER_EXCHANGE, JDBC_QUERY, TRACE_SPAN)),
                    "the fixture's three span-producing types");

            for (int i = 1; i < types.size(); i++) {
                assertTrue(types.get(i - 1).spanCount() >= types.get(i).spanCount(),
                        "listed busiest first");
            }
            assertTrue(types.stream().allMatch(type -> type.attributeCount() > 0),
                    "every span carries the shape keys at least");
        }

        @Test
        @DisplayName("the breakable count excludes what is too wide to break down")
        void breakableExcludesSearchOnly(DataSource dataSource) throws SQLException {
            // A cap of zero makes every key search-only, which is the only cap-independent assertion
            // available on a fixture whose keys all have one or two values.
            List<TraceSpanTypeRecord> capped = derived(dataSource).spanEventTypes(0);

            assertTrue(capped.stream().allMatch(type -> type.breakableCount() == 0));
            assertTrue(capped.stream().anyMatch(type -> type.attributeCount() > 0),
                    "the keys are still there, they are merely all too wide");
        }

        @Test
        @DisplayName("a type's keys carry that type's own counts")
        void keysOfEventType(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);
            List<TraceAttributeKeyRecord> http = repository.keysOf(HTTP_SERVER_EXCHANGE);

            assertFalse(http.isEmpty());
            assertTrue(http.stream().anyMatch(key -> key.id().equals(HTTP_STATUS_CODE)),
                    "the type's own declared field");
            assertTrue(http.stream().anyMatch(key -> key.id().equals(SHAPE_EVENT_TYPE)),
                    "and the shape keys every span has");
            assertTrue(http.stream().noneMatch(key -> key.id().equals(JDBC_ROWS)),
                    "but nothing another event type declares");

            // The value kind is the key's, joined from the profile-wide catalog rather than re-inferred.
            TraceAttributeKeyRecord statusCode = http.stream()
                    .filter(key -> key.id().equals(HTTP_STATUS_CODE))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    keyOf(repository, HTTP_STATUS_CODE).orElseThrow().valueKind(),
                    statusCode.valueKind());
        }

        @Test
        @DisplayName("an event type nothing recorded carries no keys")
        void unknownEventType(DataSource dataSource) throws SQLException {
            assertTrue(derived(dataSource).keysOf("jeffrey.NotARealEvent").isEmpty());
        }

        @Test
        @DisplayName("a breakdown scoped to an event type reads only that type's spans")
        void breakdownIsScoped(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            // `eventType` is a shape key, so every span carries it and its values are the event types
            // themselves -- which makes it the one key whose scoping is checkable without a fixture
            // built for it: scoped to one type, the only value left is that type.
            TraceAttributeRepository.Values scoped = repository.values(new TraceAttributeValueQuery(
                    SHAPE_EVENT_TYPE, TraceAttributeValueSortField.TOTAL_TIME, true, 50,
                    HTTP_SERVER_EXCHANGE));

            assertEquals(1, scoped.values().size());
            assertEquals(HTTP_SERVER_EXCHANGE, scoped.values().getFirst().value());

            TraceAttributeRepository.Values wide = repository.values(new TraceAttributeValueQuery(
                    SHAPE_EVENT_TYPE, TraceAttributeValueSortField.TOTAL_TIME, true, 50, null));

            assertTrue(wide.values().size() > scoped.values().size(),
                    "unscoped, the same key answers for every event type in the profile");
        }

        @Test
        @DisplayName("a scoped latency grid reads only that type's spans")
        void latencyIsScoped(DataSource dataSource) throws SQLException {
            List<TraceAttributeLatencyRecord> scoped = derived(dataSource)
                    .latency(new TraceAttributeLatencyQuery(SHAPE_EVENT_TYPE, 12, HTTP_SERVER_EXCHANGE));

            assertFalse(scoped.isEmpty());
            assertEquals(
                    List.of(HTTP_SERVER_EXCHANGE),
                    scoped.stream().map(TraceAttributeLatencyRecord::value).distinct().toList());
        }
    }

    @Nested
    @DisplayName("Latency")
    class Latency {

        @Test
        @DisplayName("a value's traces land in duration buckets")
        void valuesAreBucketed(DataSource dataSource) throws SQLException {
            List<TraceAttributeLatencyRecord> cells = derived(dataSource)
                    .latency(new TraceAttributeLatencyQuery(HTTP_STATUS_CODE, 12, null));

            assertEquals(2, cells.size(), "one bucket each, for two values one trace apiece");
            for (TraceAttributeLatencyRecord cell : cells) {
                assertEquals(1, cell.traceCount());
                assertTrue(cell.bucket() >= 10 && cell.bucket() <= 19,
                        "buckets are clamped to the grid the caller draws: " + cell.bucket());
            }
        }

        @Test
        @DisplayName("the values covered are capped")
        void coverageIsCapped(DataSource dataSource) throws SQLException {
            List<TraceAttributeLatencyRecord> cells = derived(dataSource).latency(new TraceAttributeLatencyQuery(SHAPE_EVENT_TYPE, 1, null));

            assertEquals(1, cells.stream().map(TraceAttributeLatencyRecord::value).distinct().count());
        }
    }
}
