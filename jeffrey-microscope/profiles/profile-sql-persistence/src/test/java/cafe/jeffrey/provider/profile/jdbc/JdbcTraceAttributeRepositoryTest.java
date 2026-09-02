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

import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
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
import cafe.jeffrey.provider.profile.api.TraceEventTypeRecord;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    /** Every span carries one, which is what makes the universal operator testable per trace. */
    private static final TraceAttributeKeyId SHAPE_STATUS =
            new TraceAttributeKeyId(TraceAttributeSource.SPAN_SHAPE, null, "status");
    private static final TraceAttributeKeyId JDBC_ROWS =
            new TraceAttributeKeyId(TraceAttributeSource.EVENT_FIELD, JDBC_QUERY, "rows");
    private static final TraceAttributeKeyId HTTP_STATUS_CODE =
            new TraceAttributeKeyId(TraceAttributeSource.EVENT_FIELD, HTTP_SERVER_EXCHANGE, "statusCode");

    private static final String NOTIFICATION = EventTypeName.NOTIFICATION;

    /** Keys from the maps the notification fixture's notifications attached. */
    private static final TraceAttributeKeyId NOTIFICATION_TENANT =
            TraceAttributeKeyId.notificationAttribute("tenant");
    private static final TraceAttributeKeyId NOTIFICATION_ROWS =
            TraceAttributeKeyId.notificationAttribute("rows");
    /** A key with a dot in it, which only resolves because the JSON path is quoted. */
    private static final TraceAttributeKeyId NOTIFICATION_CACHE_HIT =
            TraceAttributeKeyId.notificationAttribute("cache.hit");
    private static final TraceAttributeKeyId NOTIFICATION_REGION =
            TraceAttributeKeyId.notificationAttribute("region");
    /** The nested object beside it, which is structure rather than a value. */
    private static final TraceAttributeKeyId NOTIFICATION_PLAN =
            TraceAttributeKeyId.notificationAttribute("plan");
    private static final TraceAttributeKeyId NOTIFICATION_POOL =
            TraceAttributeKeyId.notificationAttribute("pool");

    /** The notification's own columns, exposed the way a span's shape is. */
    private static final TraceAttributeKeyId NOTIFICATION_SEVERITY =
            new TraceAttributeKeyId(TraceAttributeSource.NOTIFICATION_SHAPE, null, "severity");
    private static final TraceAttributeKeyId NOTIFICATION_TYPE =
            new TraceAttributeKeyId(TraceAttributeSource.NOTIFICATION_SHAPE, null, "type");
    /**
     * The one whose key is spelled the same as the column saying where a key came from. A row for it
     * reads {@code source = 'NOTIFICATION_SHAPE' AND attr_key = 'source'}, and pinning it here is what
     * keeps someone from "fixing" the collision by renaming the key out from under the detail panel.
     */
    private static final TraceAttributeKeyId NOTIFICATION_SOURCE =
            new TraceAttributeKeyId(TraceAttributeSource.NOTIFICATION_SHAPE, null, "source");

    /** Both traces of the fixture: the slow one, and the fast one that failed. */
    private static final int TRACES = 2;

    private static JdbcTraceAttributeRepository derived(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
        return deriveAll(dataSource);
    }

    /**
     * The same profile with the notification fixture layered on.
     * <p>
     * A separate helper rather than folding the notifications into {@link #derived} because that
     * fixture also adds a span of its own, and the span-side expectations above are counted from the
     * spans the span fixture holds.
     */
    private static JdbcTraceAttributeRepository derivedWithNotifications(DataSource dataSource)
            throws SQLException {

        TestUtils.executeSql(dataSource, "sql/events/insert-trace-spans.sql");
        TestUtils.executeSql(dataSource, "sql/events/insert-trace-notifications.sql");
        return deriveAll(dataSource);
    }

    private static JdbcTraceAttributeRepository deriveAll(DataSource dataSource) {
        DatabaseClientProvider provider = new DatabaseClientProvider(dataSource);
        // The attribute index is derived from trace_spans and trace_notifications, so the trace
        // derivation runs first -- the same order ProfileInitializerImpl uses.
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
        void hitsNameTheCarrierThatMatched(DataSource dataSource) throws SQLException {
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

        /**
         * The two negative operators answer different questions. Both fixture traces contain an
         * ERROR span, but only the slow one also has spans that are not — so the existential
         * {@code NOT_EQ} finds it, while the universal {@code NONE_EQ} correctly finds neither.
         */
        @Test
        @DisplayName("NOT_EQ is existential, NONE_EQ is universal")
        void noneEqIsUniversal(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            assertEquals(1, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(SHAPE_STATUS, TraceAttributeOperator.NOT_EQ, "ERROR")))
                    .total(), "the slow trace has spans that are not ERROR");
            assertEquals(0, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(SHAPE_STATUS, TraceAttributeOperator.NONE_EQ, "ERROR")))
                    .total(), "both traces contain an ERROR span, so neither is free of them");
            assertEquals(1, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(SHAPE_STATUS, TraceAttributeOperator.NONE_EQ, "UNSET")))
                    .total(), "only the failed trace's single span never reports UNSET");
        }

        @Test
        @DisplayName("a NONE_EQ match has no row to point at, so it produces no hit")
        void noneEqProducesNoHits(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derived(dataSource)
                    .search(search(TraceAttributeScope.TRACE, new TraceAttributeCondition(
                            SHAPE_STATUS, TraceAttributeOperator.NONE_EQ, "UNSET")));

            assertEquals(1, page.total());
            assertTrue(page.hits().isEmpty(),
                    "the condition holds because nothing matches -- there is nothing to highlight");
        }

        /**
         * Under SPAN scope the zero-count is taken per span, so a positive and a universal condition
         * together ask for one span that carries the first and is free of the second.
         */
        @Test
        @DisplayName("NONE_EQ composes with a positive condition on one span")
        void noneEqUnderSpanScope(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            TraceAttributeCondition rows = holds(JDBC_ROWS, "42");

            assertEquals(1, repository.search(search(TraceAttributeScope.SPAN, rows,
                    new TraceAttributeCondition(SHAPE_STATUS, TraceAttributeOperator.NONE_EQ, "ERROR")))
                    .total(), "the statement span carries rows=42 and is not an ERROR span");
            assertEquals(0, repository.search(search(TraceAttributeScope.SPAN, rows,
                    new TraceAttributeCondition(SHAPE_STATUS, TraceAttributeOperator.NONE_EQ, "UNSET")))
                    .total(), "that same span's status is UNSET, so requiring none fails");
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
        @DisplayName("every event type whose carriers can be searched is listed, busiest first")
        void attributeEventTypesAreListed(DataSource dataSource) throws SQLException {
            List<TraceEventTypeRecord> types = derived(dataSource).attributeEventTypes(NO_CAP);

            assertFalse(types.isEmpty());
            assertTrue(
                    types.stream().map(TraceEventTypeRecord::eventType).toList()
                            .containsAll(List.of(HTTP_SERVER_EXCHANGE, JDBC_QUERY, TRACE_SPAN)),
                    "the fixture's three span-producing types");

            for (int i = 1; i < types.size(); i++) {
                assertTrue(types.get(i - 1).carrierCount() >= types.get(i).carrierCount(),
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
            List<TraceEventTypeRecord> capped = derived(dataSource).attributeEventTypes(0);

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

    @Nested
    @DisplayName("Notification attributes")
    class NotificationAttributes {

        @Test
        @DisplayName("the map a notification attached is flattened one key per entry")
        void attributeMapIsFlattened(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertTrue(keyOf(repository, NOTIFICATION_TENANT).isPresent());
            assertTrue(keyOf(repository, NOTIFICATION_ROWS).isPresent());
            assertTrue(keyOf(repository, NOTIFICATION_POOL).isPresent());
        }

        @Test
        @DisplayName("a notification's own columns are queryable, the way a span's shape is")
        void shapeColumnsAreExposed(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertTrue(keyOf(repository, NOTIFICATION_SEVERITY).isPresent());
            assertTrue(keyOf(repository, NOTIFICATION_TYPE).isPresent());
            assertTrue(keyOf(repository, NOTIFICATION_SOURCE).isPresent(),
                    "the key spelled like the column that says where a key came from is still a key");
        }

        /** A dot in a key only survives because the JSON path is quoted; unquoted it reads as null. */
        @Test
        @DisplayName("a dotted key resolves to its own value")
        void dottedKeyResolves(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.Values values = derivedWithNotifications(dataSource)
                    .values(new TraceAttributeValueQuery(
                            NOTIFICATION_CACHE_HIT, TraceAttributeValueSortField.TRACES, true, 10, null));

            assertEquals(1, values.values().size());
            assertEquals("false", values.values().getFirst().value());
        }

        @Test
        @DisplayName("a nested object is dropped, and the scalar beside it is kept")
        void nestedObjectIsDropped(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertTrue(keyOf(repository, NOTIFICATION_PLAN).isEmpty(),
                    "an object is structure, not a value");
            assertTrue(keyOf(repository, NOTIFICATION_REGION).isPresent(),
                    "the guard rejects a key, not the whole map");
        }

        @Test
        @DisplayName("the kind is inferred from the values, so a number compares as one")
        void valueKindIsInferred(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertEquals(TraceAttributeValueKind.NUMBER,
                    keyOf(repository, NOTIFICATION_ROWS).orElseThrow().valueKind());
            assertEquals(TraceAttributeValueKind.STRING,
                    keyOf(repository, NOTIFICATION_TENANT).orElseThrow().valueKind());
        }

        @Test
        @DisplayName("deriving twice lands where deriving once did")
        void derivationIsIdempotent(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);
            long before = keyOf(repository, NOTIFICATION_TENANT).orElseThrow().carrierCount();

            repository.derive();

            assertEquals(before, keyOf(repository, NOTIFICATION_TENANT).orElseThrow().carrierCount());
        }

        @Test
        @DisplayName("a profile with no notifications carries no notification keys")
        void profileWithoutNotificationsStaysEmpty(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derived(dataSource);

            assertTrue(repository.keys().stream()
                            .noneMatch(key -> key.id().source().carrier() == TraceAttributeCarrier.NOTIFICATION),
                    "nothing said anything, so there is nothing to search");
        }
    }

    @Nested
    @DisplayName("Notification search")
    class NotificationSearch {

        @Test
        @DisplayName("a notification condition narrows to the traces it fired in")
        void severityNarrowsTraces(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derivedWithNotifications(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(NOTIFICATION_SEVERITY, "MEDIUM")));

            assertEquals(1, page.total());
            assertEquals(1, page.stats().traces());
        }

        /**
         * The guarantee the separate index exists for. A notification's {@code severity} says
         * something went wrong somewhere; the span shape's {@code status} says this span failed. A
         * search for one must never be answered by the other.
         */
        @Test
        @DisplayName("a span-shape condition matches no notification")
        void spanShapeDoesNotMatchNotifications(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            TraceAttributeRepository.SearchPage page = repository.search(search(
                    TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(
                            new TraceAttributeKeyId(TraceAttributeSource.SPAN_SHAPE, null, "status"),
                            TraceAttributeOperator.EQ,
                            "ERROR")));

            assertTrue(page.hits().stream()
                            .allMatch(hit -> hit.carrier() == TraceAttributeCarrier.SPAN),
                    "a notification is not a span that failed");
        }

        @Test
        @DisplayName("a hit names the carrier, and the span only when there is one")
        void hitNamesTheCarrier(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derivedWithNotifications(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(NOTIFICATION_TENANT, "acme")));

            assertEquals(1, page.hits().size());
            TraceAttributeRepository.Hit hit = page.hits().getFirst();
            assertEquals(TraceAttributeCarrier.NOTIFICATION, hit.carrier());
            assertEquals("tenant", hit.key());
            assertEquals("acme", hit.value());
            assertNotNull(hit.spanId(), "this one fired inside span 112");
        }

        @Test
        @DisplayName("a notification that fired outside any span still answers, with no span id")
        void hitWithoutSpanIsStillAHit(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.SearchPage page = derivedWithNotifications(dataSource)
                    .search(search(TraceAttributeScope.TRACE, holds(NOTIFICATION_POOL, "orders")));

            assertEquals(1, page.hits().size());
            TraceAttributeRepository.Hit hit = page.hits().getFirst();
            assertEquals(TraceAttributeCarrier.NOTIFICATION, hit.carrier());
            assertNull(hit.spanId(),
                    "it named a span this profile does not hold, which is an answer and not a gap");
        }

        @Test
        @DisplayName("span and notification conditions have to hold in the same trace")
        void mixedConditionsIntersect(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            TraceAttributeCondition onASpan = holds(JDBC_ROWS, "42");
            TraceAttributeCondition onANotification = holds(NOTIFICATION_TENANT, "acme");

            assertEquals(1, repository.search(
                    search(TraceAttributeScope.TRACE, onASpan, onANotification)).total(),
                    "both hold in the slow trace");
            assertEquals(0, repository.search(search(
                    TraceAttributeScope.TRACE, onASpan, holds(NOTIFICATION_TENANT, "globex"))).total(),
                    "no notification said globex, so the intersection is empty");
        }

        @Test
        @DisplayName("a numeric notification attribute compares as a number")
        void numericNotificationAttribute(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertEquals(1, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(
                            NOTIFICATION_ROWS, TraceAttributeOperator.GT, "1000"))).total());
            assertEquals(0, repository.search(search(TraceAttributeScope.TRACE,
                    new TraceAttributeCondition(
                            NOTIFICATION_ROWS, TraceAttributeOperator.GT, "99999"))).total());
        }

        /**
         * The cost guard. A search naming only spans has to emit the statement it always did — one
         * grouped scan of one table — so that notifications existing costs nothing to a reader who
         * never mentions them.
         */
        @Test
        @DisplayName("a span-only search reads one table and does not intersect")
        void spanOnlySearchIsUnchanged() {
            String sql = TraceAttributeQueries.matchingTraces(
                    search(TraceAttributeScope.TRACE, holds(JDBC_ROWS, "42")),
                    new MapSqlParameterSource());

            assertFalse(sql.contains("INTERSECT"), "one carrier is one branch: " + sql);
            assertTrue(sql.contains(TraceAttributeQueries.SPAN_ATTRIBUTES_TABLE));
            assertFalse(sql.contains(TraceAttributeQueries.NOTIFICATION_ATTRIBUTES_TABLE));
        }

        @Test
        @DisplayName("a mixed search reads both tables and intersects them")
        void mixedSearchIntersects() {
            String sql = TraceAttributeQueries.matchingTraces(
                    search(TraceAttributeScope.TRACE,
                            holds(JDBC_ROWS, "42"), holds(NOTIFICATION_TENANT, "acme")),
                    new MapSqlParameterSource());

            assertTrue(sql.contains("INTERSECT"));
            assertTrue(sql.contains(TraceAttributeQueries.SPAN_ATTRIBUTES_TABLE));
            assertTrue(sql.contains(TraceAttributeQueries.NOTIFICATION_ATTRIBUTES_TABLE));
        }

        /** Under SPAN scope a notification is grouped by itself, not by the span it fired in. */
        @Test
        @DisplayName("span scope groups notifications by the notification")
        void spanScopeGroupsNotificationsByThemselves() {
            String sql = TraceAttributeQueries.matchingTraces(
                    search(TraceAttributeScope.SPAN, holds(NOTIFICATION_TENANT, "acme")),
                    new MapSqlParameterSource());

            assertTrue(sql.contains("GROUP BY trace_id, notification_id"), sql);
        }
    }

    @Nested
    @DisplayName("Notification catalog")
    class NotificationCatalog {

        @Test
        @DisplayName("the picker's first step lists the notification type, labelled as one")
        void eventTypesIncludeNotifications(DataSource dataSource) throws SQLException {
            TraceEventTypeRecord notifications = derivedWithNotifications(dataSource)
                    .attributeEventTypes(50).stream()
                    .filter(type -> NOTIFICATION.equals(type.eventType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "without this row the second step cannot be reached, so the keys exist "
                                    + "and no reader can get to them"));

            assertEquals(TraceAttributeCarrier.NOTIFICATION, notifications.carrier());
            assertEquals(5, notifications.carrierCount(), "the five notifications that belong to a trace");
            assertEquals(0, notifications.errorCarriers(),
                    "a severity is not an outcome: nothing here failed");
            assertTrue(notifications.attributeCount() > 0);
        }

        @Test
        @DisplayName("the second step lists the notification's keys under its event type")
        void keysOfNotificationType(DataSource dataSource) throws SQLException {
            List<TraceAttributeKeyId> keys = derivedWithNotifications(dataSource)
                    .keysOf(NOTIFICATION).stream()
                    .map(TraceAttributeKeyRecord::id)
                    .toList();

            assertTrue(keys.contains(NOTIFICATION_TENANT));
            assertTrue(keys.contains(NOTIFICATION_SEVERITY));
        }

        @Test
        @DisplayName("the profile-wide catalog carries both carriers' keys side by side")
        void catalogCarriesBoth(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            assertTrue(keyOf(repository, JDBC_ROWS).isPresent(), "a span key");
            assertTrue(keyOf(repository, NOTIFICATION_TENANT).isPresent(), "a notification key");
        }

        /**
         * {@code rows} is a JDBC statement's declared field and also a key one notification attached.
         * They are different keys that share a name, and the source is what keeps them apart.
         */
        @Test
        @DisplayName("the same name under two carriers stays two keys")
        void sameNameDifferentCarrier(DataSource dataSource) throws SQLException {
            JdbcTraceAttributeRepository repository = derivedWithNotifications(dataSource);

            long rows = repository.keys().stream()
                    .filter(key -> "rows".equals(key.id().key()))
                    .count();

            assertEquals(2, rows, "one declared by a JDBC query, one attached by a notification");
        }

        @Test
        @DisplayName("the absent count is measured against the traces that said anything")
        void valuesUseTheNotificationDenominator(DataSource dataSource) throws SQLException {
            TraceAttributeRepository.Values values = derivedWithNotifications(dataSource)
                    .values(new TraceAttributeValueQuery(
                            NOTIFICATION_TENANT, TraceAttributeValueSortField.TRACES, true, 10, null));

            assertEquals(1, values.values().size());
            assertEquals(0, values.tracesWithoutKey(),
                    "one trace holds every notification, and it carried the key");
        }
    }
}
