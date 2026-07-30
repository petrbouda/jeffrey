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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real time-window query the timeline's tooltip resolves to, against DuckDB.
 *
 * <p>The tooltip used to describe the merged band under the pointer, which on a busy lane is the
 * whole recording — so it reported the lane's total wherever it was pointed. It now asks about the
 * slice of time under the pointer instead, and that only works if the window really does select just
 * the events in it. That is a property of the SQL, and nothing above it can prove it: the timeline
 * was otherwise covered only against mocked streaming repositories.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBThreadWindowQueryTest {

    private static final String FIXTURE = "sql/events/insert-thread-timeline-window.sql";

    /** The pool behind one collapsed lane: two workers with a Java id, one matched on its OS id. */
    private static final List<ThreadInfo> POOL = List.of(
            new ThreadInfo(51, 21, "http-nio-8080-exec-1"),
            new ThreadInfo(52, 22, "http-nio-8080-exec-2"),
            new ThreadInfo(53, -1, "http-nio-8080-exec-3"));

    /** Counts what the window selected, the way the tooltip's builder does. */
    private static final class CountingBuilder implements RecordBuilder<GenericRecord, List<GenericRecord>> {
        private final List<GenericRecord> records = new ArrayList<>();

        @Override
        public void onRecord(GenericRecord record) {
            records.add(record);
        }

        @Override
        public List<GenericRecord> build() {
            return records;
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

    private static List<GenericRecord> inWindow(
            DataSource dataSource, List<ThreadInfo> threads, long fromMillis, long toMillis) {

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.SOCKET_READ)
                .withSpecifiedThreads(threads)
                .withTimeRange(new RelativeTimeRange(
                        Duration.ofMillis(fromMillis), Duration.ofMillis(toMillis)))
                // The tooltip has no use for the thread columns, but a test asserting which threads a
                // lane matched has to be able to see them. Neither filter under test is affected.
                .withThreads()
                .withJsonFields();

        return streamRepository(dataSource).genericStreaming(configurer, new CountingBuilder());
    }

    /**
     * The shape of the bug: a lane holds six events, but the millisecond under the pointer holds
     * three of them.
     */
    @Test
    void aWindowSelectsOnlyTheEventsInIt(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);

        assertEquals(3, inWindow(dataSource, POOL, 100, 101).size(),
                "Only the events starting in millisecond 100");
        assertEquals(6, inWindow(dataSource, POOL, 0, 1_000).size(),
                "The lane's own total, for contrast");
    }

    /**
     * The upper bound is exclusive, which is what lets the windows of two neighbouring pixels tile.
     * If it were inclusive the event on the boundary would be counted under both of them.
     */
    @Test
    void adjacentWindowsPartitionTheirEvents(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);

        int first = inWindow(dataSource, POOL, 100, 101).size();
        int second = inWindow(dataSource, POOL, 101, 102).size();

        assertEquals(3, first);
        assertEquals(1, second);
        assertEquals(4, first + second,
                "Neither window may claim the other's event");
        assertEquals(4, inWindow(dataSource, POOL, 100, 102).size(),
                "Together they are exactly the window that spans them");
    }

    @Test
    void aQuietWindowSelectsNothing(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);

        assertTrue(inWindow(dataSource, POOL, 200, 300).isEmpty(),
                "Nothing happened between milliseconds 200 and 300");
    }

    /**
     * A collapsed lane stands for every thread behind it, including one that never got a Java id and
     * has to be matched on its OS id instead.
     */
    @Test
    void aCollapsedLaneCountsAcrossItsMembers(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);

        assertEquals(3, inWindow(dataSource, POOL, 100, 101).size());
        assertEquals(1, inWindow(dataSource, POOL, 100, 101).stream()
                        .filter(record -> record.thread().osThreadId() == 53)
                        .count(),
                "The worker with no Java id is matched on its OS id");
    }

    /**
     * A thread outside the lane shares the busiest millisecond. It must not be swept in — least of
     * all through the {@code -1} java id the native worker carries.
     */
    @Test
    void aWindowStaysWithinItsOwnLane(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, FIXTURE);

        assertEquals(1, inWindow(dataSource, POOL.subList(0, 1), 100, 101).size(),
                "One thread is still one thread");
        assertTrue(inWindow(dataSource, POOL, 100, 101).stream()
                        .noneMatch(record -> "main".equals(record.thread().name())),
                "A thread outside the lane never belongs to its window");
    }
}
