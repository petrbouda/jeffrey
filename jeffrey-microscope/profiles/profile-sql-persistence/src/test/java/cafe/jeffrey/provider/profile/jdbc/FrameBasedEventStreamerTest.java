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
import cafe.jeffrey.provider.profile.api.SecondValue;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contrasts the bucketed {@code frameBasedTimeseriesStreamer} against the per-event
 * {@code frameBasedEventStreamer}. The fixture puts several weighted events on the same stack in the same
 * second, so the bucketed query collapses them (losing the sample count) while the per-event query keeps one
 * observation per event — the behaviour the weighted OTLP export relies on to round-trip the exact count.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class FrameBasedEventStreamerTest {

    private static final String FIXTURE = "sql/events/insert-weighted-frame-events.sql";
    private static final Type ALLOC = Type.fromCode("alloc");

    // Fixture totals: 5 events, weights 10+20+30 (stack 4001) + 40+50 (stack 4002) = 150.
    private static final int EXPECTED_EVENT_COUNT = 5;
    private static final long EXPECTED_TOTAL_WEIGHT = 150L;

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

    private static final class CollectingBuilder implements RecordBuilder<TimeseriesRecord, List<TimeseriesRecord>> {
        private final List<TimeseriesRecord> records = new ArrayList<>();

        @Override
        public void onRecord(TimeseriesRecord record) {
            records.add(record);
        }

        @Override
        public List<TimeseriesRecord> build() {
            return records;
        }
    }

    @Test
    void perSecondStreamerCollapsesSameSecondEventsIntoBuckets(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);
        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(ALLOC).withWeight(true);

        List<TimeseriesRecord> records = streamRepository(dataSource)
                .frameBasedTimeseriesStreamer(configurer, new CollectingBuilder());

        // Two stacks; each stack's same-second events collapse to a single bucket -> one SecondValue per stack.
        assertEquals(2, records.size());
        List<SecondValue> buckets = records.stream().flatMap(r -> r.values().stream()).toList();
        assertEquals(2, buckets.size(), "same-second events must collapse into one bucket per stack");
        assertEquals(EXPECTED_TOTAL_WEIGHT, buckets.stream().mapToLong(SecondValue::value).sum());
        // second slot is a whole-second index in this mode.
        assertEquals(List.of(0L, 1L), buckets.stream().map(SecondValue::second).sorted().toList());
    }

    @Test
    void perEventStreamerKeepsOneObservationPerEvent(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);
        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(ALLOC).withWeight(true);

        List<TimeseriesRecord> records = streamRepository(dataSource)
                .frameBasedEventStreamer(configurer, new CollectingBuilder());

        // Still grouped per stack, but every original event yields its own observation.
        assertEquals(2, records.size());
        List<SecondValue> observations = records.stream().flatMap(r -> r.values().stream()).toList();
        assertEquals(EXPECTED_EVENT_COUNT, observations.size(), "one observation per original event");
        assertEquals(EXPECTED_TOTAL_WEIGHT, observations.stream().mapToLong(SecondValue::value).sum());
        // second slot now carries the raw millisecond offset (start_timestamp_from_beginning), not a second index.
        assertEquals(List.of(100L, 500L, 900L, 1100L, 1200L),
                observations.stream().map(SecondValue::second).sorted().toList());
    }
}
