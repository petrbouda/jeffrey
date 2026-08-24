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

import cafe.jeffrey.provider.profile.api.CachingProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.EventTypeWithFields;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DuckDBTest(migration = "classpath:db/migration/profile")
class CachingProfileEventTypeRepositoryTest {

    /**
     * Counts how often the profile-wide summaries reach the database, so a cache hit is proven by
     * the query not running rather than by the answer merely looking right.
     */
    private static final class CountingRepository implements ProfileEventTypeRepository {

        private final ProfileEventTypeRepository delegate;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingRepository(ProfileEventTypeRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<EventSummary> eventSummaries() {
            calls.incrementAndGet();
            return delegate.eventSummaries();
        }

        @Override
        public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
            return delegate.singleFieldsByEventType(type);
        }

        @Override
        public List<FieldDescription> eventColumns(Type type) {
            return delegate.eventColumns(type);
        }

        @Override
        public List<EventSummary> eventSummaries(List<Type> types) {
            return delegate.eventSummaries(types);
        }

        @Override
        public List<EventSummary> eventSummaries(List<Type> types, SpanScope spanScope) {
            return delegate.eventSummaries(types, spanScope);
        }
    }

    private static CountingRepository counting(DataSource dataSource) {
        DatabaseClientProvider provider = new DatabaseClientProvider(dataSource);
        return new CountingRepository(
                new JdbcProfileEventTypeRepository(new DuckDBSQLFormatter(), provider));
    }

    private static CachingProfileEventTypeRepository caching(
            DataSource dataSource, CountingRepository counted) {

        return new CachingProfileEventTypeRepository(
                counted, new JdbcProfileCacheRepository(new DatabaseClientProvider(dataSource)));
    }

    private static List<EventSummary> sorted(List<EventSummary> summaries) {
        return summaries.stream()
                .sorted(Comparator.comparing(EventSummary::name))
                .toList();
    }

    @Test
    @DisplayName("the second call is served from the cache without touching the database")
    void secondCallDoesNotReachTheDatabase(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
        CountingRepository counted = counting(dataSource);
        CachingProfileEventTypeRepository repository = caching(dataSource, counted);

        repository.eventSummaries();
        repository.eventSummaries();
        repository.eventSummaries();

        assertEquals(1, counted.calls.get());
    }

    /**
     * The cache round-trips through JSON in a BLOB column, so every field of every summary has to
     * survive serialization -- a silently dropped enum or map would hand the Event Viewer and the
     * feature checks a different answer on the second read than on the first.
     */
    @Test
    @DisplayName("a cached summary is identical to the one the database produced")
    void cachedSummariesEqualTheComputedOnes(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
        CountingRepository counted = counting(dataSource);
        CachingProfileEventTypeRepository repository = caching(dataSource, counted);

        List<EventSummary> computed = sorted(repository.eventSummaries());
        List<EventSummary> cached = sorted(repository.eventSummaries());

        assertFalse(computed.isEmpty());
        assertEquals(computed, cached);
    }

    @Test
    @DisplayName("the narrowed overloads are never served from the profile-wide entry")
    void narrowedOverloadsAlwaysDelegate(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
        CountingRepository counted = counting(dataSource);
        CachingProfileEventTypeRepository repository = caching(dataSource, counted);

        repository.eventSummaries();
        List<EventSummary> narrowed = repository.eventSummaries(List.of(Type.EXECUTION_SAMPLE));

        // One entry back, not the whole profile: the narrowed answer must not come from the
        // profile-wide cache entry.
        assertEquals(1, narrowed.size());
        assertEquals(Type.EXECUTION_SAMPLE.code(), narrowed.getFirst().name());
    }

    /**
     * The window that makes a persistent cache dangerous: a profile is reachable over HTTP before
     * initialization has written {@code event_types}, and anything asking for the summaries then
     * gets an empty list. Caching that would tell the Event Viewer and the feature checks for the
     * rest of the profile's life that it contains no events at all.
     */
    @Test
    @DisplayName("an empty answer is recomputed rather than cached")
    void emptyAnswerIsNeverCached(DataSource dataSource) throws SQLException {
        CountingRepository counted = counting(dataSource);
        CachingProfileEventTypeRepository repository = caching(dataSource, counted);

        // Asked while the profile is still empty, the way a request arriving mid-initialization does.
        assertEquals(List.of(), repository.eventSummaries());

        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");

        assertFalse(repository.eventSummaries().isEmpty());
        assertEquals(2, counted.calls.get());
    }
}
