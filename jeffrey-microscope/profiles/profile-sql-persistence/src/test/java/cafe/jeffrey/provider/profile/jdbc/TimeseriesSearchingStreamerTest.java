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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.TimeseriesSearchRecord;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the searching timeseries query (SIMPLE_SEARCH): the search pattern is a regular
 * expression tested against the composed {@code class#method} frame name, mirroring the client-side
 * flamegraph highlight. In particular a dotted search like {@code JsonReader.<init>} — the form the
 * flamegraph renders and users copy — must match the stored {@code JsonReader} + {@code <init>}
 * frame, which the previous per-column LIKE matching could never do.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class TimeseriesSearchingStreamerTest {

    private static final String FIXTURE = "sql/events/insert-search-frame-events.sql";
    private static final Type ALLOC = Type.fromCode("alloc");

    // Fixture: three single-event stacks in second 0 — constructor stack (weight 10),
    // regular-method stack (weight 20), class-less native stack (weight 30).
    private static final long TOTAL_WEIGHT = 60L;
    private static final long CONSTRUCTOR_WEIGHT = 10L;
    private static final long NATIVE_WEIGHT = 30L;

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

    private static final class CollectingBuilder
            implements RecordBuilder<TimeseriesSearchRecord, List<TimeseriesSearchRecord>> {
        private final List<TimeseriesSearchRecord> records = new ArrayList<>();

        @Override
        public void onRecord(TimeseriesSearchRecord record) {
            records.add(record);
        }

        @Override
        public List<TimeseriesSearchRecord> build() {
            return records;
        }
    }

    private static List<TimeseriesSearchRecord> search(DataSource dataSource, String pattern) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(ALLOC)
                .withWeight(true)
                .withSearchPattern(pattern);
        return streamRepository(dataSource).timeseriesSearchingStreamer(configurer, new CollectingBuilder());
    }

    private static void assertMatched(List<TimeseriesSearchRecord> records, long expectedMatched) {
        assertEquals(1, records.size(), "all fixture events fall into a single second bucket");
        assertEquals(TOTAL_WEIGHT, records.getFirst().total());
        assertEquals(expectedMatched, records.getFirst().matched());
    }

    @Nested
    class ConstructorFrames {

        @Test
        void dottedSearchMatchesConstructorFrame(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "JsonReader.<init>"), CONSTRUCTOR_WEIGHT);
        }

        @Test
        void hashSearchMatchesConstructorFrame(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "JsonReader#<init>"), CONSTRUCTOR_WEIGHT);
        }

        @Test
        void bareMethodSearchMatchesConstructorFrame(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "<init>"), CONSTRUCTOR_WEIGHT);
        }
    }

    @Nested
    class RegularFrames {

        @Test
        void classOnlySearchMatches(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "com.example.Service"), 20L);
        }

        @Test
        void classAndMethodSearchMatches(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "Service.process"), 20L);
        }

        @Test
        void unmatchedSearchMatchesNothing(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "does.not.Exist"), 0L);
        }
    }

    @Nested
    class NativeFrames {

        @Test
        void methodOnlySearchMatchesFrameWithoutClassName(DataSource dataSource) throws SQLException {
            assertMatched(search(dataSource, "clone3"), NATIVE_WEIGHT);
        }
    }
}
