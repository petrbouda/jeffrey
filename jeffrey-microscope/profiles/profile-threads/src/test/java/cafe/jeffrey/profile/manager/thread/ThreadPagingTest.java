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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import cafe.jeffrey.profile.thread.ThreadCommon;
import cafe.jeffrey.profile.thread.ThreadGroup;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadInfoProvider;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadPeriod;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.profile.thread.ThreadRow;
import cafe.jeffrey.profile.thread.ThreadSort;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ThreadInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The timeline draws one lane per group of identically named threads and pages through both levels:
 * the lanes themselves, and the members behind a lane once it is opened.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThreadPagingTest {

    private static final Instant RECORDING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration RECORDING_LENGTH = Duration.ofMinutes(1);

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    @Mock
    ProfileEventTypeRepository eventTypeRepository;

    private static ThreadRow row(String name, long javaId, long events, long duration) {
        return row(name, javaId, events, duration, List.of());
    }

    private static ThreadRow row(
            String name, long javaId, long events, long duration, List<ThreadPeriod> socketRead) {

        return new ThreadRow(
                duration,
                events,
                new ThreadInfo(javaId + 1000, javaId, name),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                socketRead,
                List.of(), List.of(), List.of());
    }

    /**
     * Six threads in four groups: a numbered pool of two, another of two, and two threads that are
     * alone under their own name.
     */
    private static final List<ThreadRow> ROWS = List.of(
            row("main", 1, 9_401, 60_000),
            row("http-nio-8080-exec-17", 2, 58_204, 59_000),
            row("http-nio-8080-exec-3", 3, 51_880, 58_000),
            row("oracleApp:connection-adder-118", 4, 9_412, 12_000),
            row("oracleApp:connection-adder-42", 5, 9_110, 11_000),
            row("Keep-Alive-Timer-6", 6, 4_002, 57_000));

    private ThreadManager manager(List<ThreadRow> rows) {
        ProfileInfo profileInfo = org.mockito.Mockito.mock(ProfileInfo.class);
        when(profileInfo.duration()).thenReturn(RECORDING_LENGTH);

        ThreadInfoProvider provider = () -> new ThreadRoot(new ThreadCommon(60_000, false, null), rows);
        return new ThreadManagerImpl(
                profileInfo, eventRepository, eventStreamRepository, eventTypeRepository, provider);
    }

    private ThreadGroupPage groups(ThreadSort sort, String filter, int offset, int limit) {
        return manager(ROWS).threadGroups(new ThreadPageQuery(sort, filter, offset, limit));
    }

    private static List<String> keys(ThreadGroupPage page) {
        return page.groups().stream().map(ThreadGroup::key).toList();
    }

    @Nested
    class Grouping {

        @Test
        void foldsNumberedWorkersOfAPoolIntoOneLane() {
            ThreadGroupPage page = groups(ThreadSort.EVENT_COUNT, null, 0, 50);

            assertEquals(4, page.groups().size(), "Six threads make four lanes");
            assertEquals(4, page.totalGroups());
            assertEquals(6, page.totalThreads(), "The thread count is what the lanes stand in for");
            assertTrue(keys(page).contains("http-nio-8080-exec-*"));
            assertTrue(keys(page).contains("oracleApp:connection-adder-*"));
        }

        @Test
        void countsTheThreadsBehindEachLane() {
            ThreadGroup pool = groups(ThreadSort.EVENT_COUNT, null, 0, 50).groups().stream()
                    .filter(group -> group.key().equals("http-nio-8080-exec-*"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(2, pool.threadCount());
            assertTrue(pool.isCollapsed());
            assertEquals(58_204 + 51_880, pool.lane().eventsCount(), "A lane's events are its members'");
        }

        /**
         * A lane standing for one thread has to stay that thread: everything that acts on a thread
         * needs its ids, and a group of one has no reason to lose them.
         */
        @Test
        void leavesAThreadThatIsAloneUntouched() {
            ThreadGroup single = groups(ThreadSort.EVENT_COUNT, null, 0, 50).groups().stream()
                    .filter(group -> group.key().equals("main"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(1, single.threadCount());
            assertFalse(single.isCollapsed());
            assertEquals(1, single.lane().threadInfo().javaId());
            assertEquals("main", single.lane().threadInfo().name());
        }

        /**
         * The case the grouping exists for: hundreds of threads sharing one name exactly, each with a
         * single event, become one lane carrying all of their marks.
         */
        @Test
        void putsAWholePoolsEventsOnOneLane() {
            List<ThreadRow> pool = IntStream.range(0, 351)
                    .mapToObj(i -> row(
                            "oracleApp:connection-adder", i, 1, 58_000,
                            List.of(new ThreadPeriod(i * 150_000_000L, 1_000_000, 1))))
                    .toList();

            ThreadGroupPage page = manager(pool)
                    .threadGroups(new ThreadPageQuery(ThreadSort.EVENT_COUNT, null, 0, 50));

            assertEquals(1, page.groups().size());
            ThreadGroup group = page.groups().getFirst();
            assertEquals(351, group.threadCount());
            assertEquals(351, group.lane().eventsCount());
            assertEquals(351, group.lane().socketRead().stream()
                    .mapToInt(ThreadPeriod::eventCount).sum(), "Every member's event survives the union");
            assertFalse(group.lane().socketRead().isEmpty());
        }
    }

    @Nested
    class Ordering {

        @Test
        void putsTheBusiestLaneFirst() {
            assertEquals("http-nio-8080-exec-*", keys(groups(ThreadSort.EVENT_COUNT, null, 0, 1)).getFirst());
        }

        @Test
        void ordersByTheLongestLivedMemberForLifespan() {
            assertEquals("main", keys(groups(ThreadSort.LIFESPAN, null, 0, 1)).getFirst());
        }

        @Test
        void ordersByNameWhenAsked() {
            assertEquals("Keep-Alive-Timer-*", keys(groups(ThreadSort.NAME, null, 0, 1)).getFirst());
        }

        @Test
        void keepsPagesStableAndNonOverlapping() {
            ThreadGroupPage first = groups(ThreadSort.EVENT_COUNT, null, 0, 2);
            ThreadGroupPage again = groups(ThreadSort.EVENT_COUNT, null, 0, 2);
            ThreadGroupPage second = groups(ThreadSort.EVENT_COUNT, null, 2, 2);

            assertEquals(keys(first), keys(again));
            assertTrue(keys(first).stream().noneMatch(keys(second)::contains));
        }
    }

    @Nested
    class Filtering {

        @Test
        void matchesTheLaneName() {
            assertEquals(1, groups(ThreadSort.EVENT_COUNT, "connection-adder", 0, 50).matchedGroups());
        }

        /**
         * Searching for one worker must find the lane it is folded into — otherwise grouping would
         * make individual threads unreachable by name.
         */
        @Test
        void alsoMatchesAMemberInsideTheLane() {
            ThreadGroupPage page = groups(ThreadSort.EVENT_COUNT, "exec-17", 0, 50);

            assertEquals(1, page.matchedGroups());
            assertEquals("http-nio-8080-exec-*", keys(page).getFirst());
        }

        @Test
        void narrowsTheMatchedCountButNotTheTotals() {
            ThreadGroupPage page = groups(ThreadSort.EVENT_COUNT, "connection-adder", 0, 50);

            assertEquals(1, page.matchedGroups());
            assertEquals(4, page.totalGroups());
            assertEquals(6, page.totalThreads());
        }
    }

    @Nested
    class Members {

        private ThreadPage members(String key, int offset, int limit) {
            return manager(ROWS)
                    .threadGroupMembers(new ThreadMembersQuery(key, ThreadSort.EVENT_COUNT, offset, limit));
        }

        @Test
        void returnTheThreadsBehindALane() {
            ThreadPage page = members("http-nio-8080-exec-*", 0, 50);

            assertEquals(2, page.rows().size());
            assertEquals(2, page.matchedCount());
            assertEquals("http-nio-8080-exec-17", page.rows().getFirst().threadInfo().name());
        }

        /**
         * Opening a pool of 351 must not put 351 lanes on the page — the whole reason the timeline
         * pages in the first place.
         */
        @Test
        void arePagedLikeTheLanesThemselves() {
            List<ThreadRow> pool = IntStream.range(0, 351)
                    .mapToObj(i -> row("oracleApp:connection-adder", i, 351 - i, 58_000))
                    .toList();

            ThreadPage first = manager(pool).threadGroupMembers(
                    new ThreadMembersQuery("oracleApp:connection-adder", ThreadSort.EVENT_COUNT, 0, 50));

            assertEquals(50, first.rows().size());
            assertEquals(351, first.matchedCount());
            assertTrue(first.hasMore());

            ThreadPage last = manager(pool).threadGroupMembers(
                    new ThreadMembersQuery("oracleApp:connection-adder", ThreadSort.EVENT_COUNT, 350, 50));

            assertEquals(1, last.rows().size());
            assertFalse(last.hasMore());
        }

        @Test
        void areEmptyForALaneThatDoesNotExist() {
            assertTrue(members("no-such-group", 0, 50).rows().isEmpty());
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
                    () -> new ThreadMembersQuery("  ", ThreadSort.EVENT_COUNT, 0, 50));
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadMembersQuery("pool-*", ThreadSort.EVENT_COUNT, 0, 0));
        }
    }
}
