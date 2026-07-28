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

package cafe.jeffrey.profile.manager.thread;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.thread.ThreadCommon;
import cafe.jeffrey.profile.thread.ThreadInfoProvider;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.profile.thread.ThreadRow;
import cafe.jeffrey.profile.thread.ThreadSort;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ThreadInfo;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeline hands out one page at a time, so the order and the filter have to be decided here —
 * a browser holding fifty threads can only sort and filter those fifty.
 */
@ExtendWith(MockitoExtension.class)
class ThreadPagingTest {

    @Mock
    ProfileInfo profileInfo;

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    @Mock
    ProfileEventTypeRepository eventTypeRepository;

    private static ThreadRow row(String name, long javaId, long events, long duration) {
        return new ThreadRow(
                duration,
                events,
                new ThreadInfo(javaId + 1000, javaId, name),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Two pools plus a couple of singletons — the shape of the recording that motivated paging.
     */
    private static final List<ThreadRow> ROWS = List.of(
            row("main", 1, 9_401, 60_000),
            row("http-nio-8080-exec-17", 2, 58_204, 59_000),
            row("http-nio-8080-exec-3", 3, 51_880, 58_000),
            row("oracleApp:connection-adder-118", 4, 9_412, 12_000),
            row("oracleApp:connection-adder-42", 5, 9_110, 11_000),
            row("Keep-Alive-Timer-6", 6, 4_002, 57_000));

    private ThreadManager manager(List<ThreadRow> rows) {
        ThreadInfoProvider provider = () -> new ThreadRoot(new ThreadCommon(60_000, false, null), rows);
        return new ThreadManagerImpl(
                profileInfo, eventRepository, eventStreamRepository, eventTypeRepository, provider);
    }

    private ThreadPage page(ThreadSort sort, String filter, int offset, int limit) {
        return manager(ROWS).threadPage(new ThreadPageQuery(sort, filter, offset, limit));
    }

    private static List<String> names(ThreadPage page) {
        return page.rows().stream().map(row -> row.threadInfo().name()).toList();
    }

    @Nested
    class Ordering {

        @Test
        void putsTheBusiestThreadsOnTheFirstPage() {
            assertEquals(
                    List.of("http-nio-8080-exec-17", "http-nio-8080-exec-3"),
                    names(page(ThreadSort.EVENT_COUNT, null, 0, 2)));
        }

        @Test
        void ordersByLifespanWhenAsked() {
            assertEquals("main", names(page(ThreadSort.LIFESPAN, null, 0, 1)).getFirst());
        }

        @Test
        void ordersByNameWhenAsked() {
            assertEquals(
                    List.of("Keep-Alive-Timer-6", "http-nio-8080-exec-17"),
                    names(page(ThreadSort.NAME, null, 0, 2)));
        }

        /**
         * Threads that tie on the sort key must not swap places between requests, or paging would
         * show one of them twice and skip the other.
         */
        @Test
        void breaksTiesTheSameWayEveryTime() {
            List<ThreadRow> tied = IntStream.range(0, 40)
                    .mapToObj(i -> row("oracleApp:connection-adder-" + i, i, 500, 1_000))
                    .toList();
            ThreadManager manager = manager(tied);

            ThreadPage first = manager.threadPage(new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, 0, 10));
            ThreadPage again = manager.threadPage(new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, 0, 10));
            ThreadPage second = manager.threadPage(new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, 10, 10));

            assertEquals(names(first), names(again));
            assertTrue(names(first).stream().noneMatch(names(second)::contains),
                    "Consecutive pages must not overlap");
        }
    }

    @Nested
    class Filtering {

        @Test
        void matchesASubstringOfTheThreadName() {
            ThreadPage page = page(ThreadSort.EVENT_COUNT, "connection-adder", 0, 50);

            assertEquals(2, page.rows().size());
            assertEquals(2, page.matchedCount());
        }

        @Test
        void ignoresCase() {
            assertEquals(2, page(ThreadSort.EVENT_COUNT, "CONNECTION-ADDER", 0, 50).matchedCount());
        }

        /**
         * The footer reads "showing 2 of 2 matching threads · 6 in the recording", so the two counts
         * have to mean different things.
         */
        @Test
        void narrowsTheMatchedCountButNotTheTotal() {
            ThreadPage page = page(ThreadSort.EVENT_COUNT, "connection-adder", 0, 50);

            assertEquals(2, page.matchedCount());
            assertEquals(6, page.totalCount());
        }

        @Test
        void treatsABlankFilterAsNoFilter() {
            assertEquals(6, page(ThreadSort.EVENT_COUNT, "   ", 0, 50).matchedCount());
        }
    }

    @Nested
    class Slicing {

        @Test
        void reportsMoreUntilTheLastPageIsReached() {
            assertTrue(page(ThreadSort.EVENT_COUNT, null, 0, 2).hasMore());
            assertFalse(page(ThreadSort.EVENT_COUNT, null, 4, 2).hasMore());
        }

        @Test
        void returnsAnEmptyPagePastTheEnd() {
            ThreadPage page = page(ThreadSort.EVENT_COUNT, null, 500, 50);

            assertTrue(page.rows().isEmpty());
            assertEquals(6, page.matchedCount());
            assertFalse(page.hasMore());
        }

        @Test
        void carriesTheOffsetBackSoThePageKnowsWhereItIs() {
            assertEquals(2, page(ThreadSort.EVENT_COUNT, null, 2, 2).offset());
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsAnUnusableQuery() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadPageQuery(null, null, 0, 50));
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, -1, 50));
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, 0, 0));
        }
    }
}
