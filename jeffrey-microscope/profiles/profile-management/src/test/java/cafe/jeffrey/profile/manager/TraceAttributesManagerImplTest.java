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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueKind;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceAttributesManagerImplTest {

    private static final long TRACE = 7L;
    private static final long SPAN = 71L;
    private static final long MS = 1_000_000L;

    private static final TraceAttributeKeyId TENANT = TraceAttributeKeyId.attribute("tenant");

    @Mock
    TraceAttributeRepository repository;

    private TraceAttributesManagerImpl manager() {
        return new TraceAttributesManagerImpl(repository);
    }

    private static TraceAttributeKeyRecord key(TraceAttributeKeyId id, long distinctValues) {
        return new TraceAttributeKeyRecord(
                id, TraceAttributeValueKind.STRING, distinctValues, 100, 50);
    }

    private static TraceSummaryRecord trace(long traceId) {
        return new TraceSummaryRecord(
                traceId, "GET /orders", "SERVER", "jeffrey.HttpServerExchange",
                0, 0, 120 * MS, 4, 0, true);
    }

    private static TraceAttributeSearchQuery search() {
        return new TraceAttributeSearchQuery(
                List.of(), TraceAttributeScope.TRACE, TraceSortField.DURATION, true, 50, 0);
    }

    @Nested
    @DisplayName("The catalog")
    class Catalog {

        @Test
        @DisplayName("a key wider than the cap is marked search-only")
        void wideKeyIsSearchOnly() {
            when(repository.keys()).thenReturn(List.of(
                    key(TENANT, 7),
                    key(TraceAttributeKeyId.attribute("user.id"),
                            TraceAttributesManager.SEARCH_ONLY_ABOVE + 1)));

            List<TraceAttributeKeyRow> keys = manager().keys();

            assertFalse(keys.getFirst().searchOnly(), "seven tenants are worth breaking down");
            assertTrue(keys.getLast().searchOnly(),
                    "a key with a value per trace has no breakdown worth drawing");
        }

        @Test
        @DisplayName("the source and the owner travel with the key")
        void identityIsCarried() {
            TraceAttributeKeyId rows = new TraceAttributeKeyId(
                    TraceAttributeSource.EVENT_FIELD, "jeffrey.JdbcQuery", "rows");
            when(repository.keys()).thenReturn(List.of(key(rows, 12)));

            TraceAttributeKeyRow row = manager().keys().getFirst();

            assertEquals("EVENT_FIELD", row.source());
            assertEquals("jeffrey.JdbcQuery", row.owner());
            assertEquals("rows", row.key());
        }
    }

    @Nested
    @DisplayName("Search")
    class Search {

        private void searchReturns(List<TraceAttributeRepository.Hit> hits) {
            when(repository.search(any())).thenReturn(new TraceAttributeRepository.SearchPage(
                    List.of(trace(TRACE)),
                    hits,
                    1,
                    new TraceAttributeRepository.Stats(1, 0, 120 * MS, 120 * MS, 120 * MS, 120 * MS)));
        }

        @Test
        @DisplayName("ids cross the wire as hex, not as numbers")
        void idsAreHex() {
            searchReturns(List.of(new TraceAttributeRepository.Hit(TRACE, SPAN, "tenant", "acme")));

            TraceAttributeSearchResult result = manager().search(search());

            TraceAttributeSearchResult.Match match = result.matches().getFirst();
            assertEquals(TraceIds.hex(TRACE), match.trace().traceId());
            assertEquals(TraceIds.hex(SPAN), match.hits().getFirst().spanId());
        }

        @Test
        @DisplayName("the hits are gathered onto the trace they belong to")
        void hitsAreGrouped() {
            searchReturns(List.of(
                    new TraceAttributeRepository.Hit(TRACE, SPAN, "tenant", "acme"),
                    new TraceAttributeRepository.Hit(TRACE, SPAN + 1, "cache.hit", "false")));

            TraceAttributeSearchResult result = manager().search(search());

            assertEquals(1, result.matches().size());
            assertEquals(2, result.matches().getFirst().hits().size());
        }

        /**
         * A trace where every span carries the value answers with the first few; the rest are the
         * same fact repeated, and they would push the trace itself off the row.
         */
        @Test
        @DisplayName("one trace cannot flood the page with its own hits")
        void hitsAreCappedPerTrace() {
            searchReturns(LongStream.range(0, 50)
                    .mapToObj(index -> new TraceAttributeRepository.Hit(
                            TRACE, SPAN + index, "tenant", "acme"))
                    .toList());

            TraceAttributeSearchResult result = manager().search(search());

            assertTrue(result.matches().getFirst().hits().size() < 50);
            assertFalse(result.matches().getFirst().hits().isEmpty());
        }

        @Test
        @DisplayName("a trace nothing matched still comes back, with no hits")
        void unmatchedTraceKeepsItsRow() {
            searchReturns(List.of());

            TraceAttributeSearchResult result = manager().search(search());

            assertEquals(1, result.matches().size());
            assertTrue(result.matches().getFirst().hits().isEmpty());
        }
    }

    @Nested
    @DisplayName("Values")
    class Values {

        private static final TraceAttributeValueRecord ACME = new TraceAttributeValueRecord(
                "acme", 12, 500 * MS, 20 * MS, 90 * MS, 120 * MS, 1);

        @Test
        @DisplayName("a list shorter than the key is reported as truncated")
        void truncationIsStated() {
            when(repository.values(any()))
                    .thenReturn(new TraceAttributeRepository.Values(List.of(ACME), 3));
            when(repository.keys()).thenReturn(List.of(key(TENANT, 7)));

            TraceAttributeValues values = manager().values(new TraceAttributeValueQuery(
                    TENANT, TraceAttributeValueSortField.TOTAL_TIME, true, 50));

            assertEquals(7, values.distinctValues());
            assertTrue(values.truncated(), "one of seven values is the top of the key, not the key");
            assertEquals(3, values.tracesWithoutKey());
        }

        @Test
        @DisplayName("the whole key is not reported as truncated")
        void completeListIsNotTruncated() {
            when(repository.values(any()))
                    .thenReturn(new TraceAttributeRepository.Values(List.of(ACME), 0));
            when(repository.keys()).thenReturn(List.of(key(TENANT, 1)));

            assertFalse(manager().values(new TraceAttributeValueQuery(
                    TENANT, TraceAttributeValueSortField.TOTAL_TIME, true, 50)).truncated());
        }
    }
}
