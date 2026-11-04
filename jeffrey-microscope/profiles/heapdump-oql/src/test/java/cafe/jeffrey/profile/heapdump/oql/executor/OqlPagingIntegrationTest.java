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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.heapdump.model.OQLResumePoint;
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A query's rows read a page at a time: each page starts where the one before it ended, says whether
 * more follow, and the pages together are the query's rows, none twice — for a query planned to SQL
 * and for one the Java executor walks. A walked query also resumes from the point a page hands back,
 * inside one object's rows or past it, without reading the objects before.
 */
@DuckDBTest(migration = "classpath:db/migration/heap-dump-index")
class OqlPagingIntegrationTest {

    /** Seven strings, read three at a time: two full pages and one of a single row. */
    private static final int STRINGS = 7;
    private static final int PAGE = 3;
    private static final int UNPAGED = 100;

    private static final long FIRST_STRING_ID = 100;
    /** The first thread holds the first five strings, the second the last two: seven rows from two objects. */
    private static final long FIRST_THREAD = 50;
    private static final long SECOND_THREAD = 51;
    private static final int HELD_BY_THE_FIRST = 5;
    private static final long THREAD_CLASS = 12;

    private static final String ALL_STRINGS = "SELECT s FROM java.lang.String s";
    private static final String WHAT_THE_THREADS_HOLD = "SELECT outbounds(t) FROM java.lang.Thread t";
    private static final String WHAT_THE_THREADS_HOLD_SORTED = WHAT_THE_THREADS_HOLD + " ORDER BY sizeof(t)";

    private final OqlEngine engine = new OqlEngine();
    private TestHeapView view;

    @BeforeEach
    void seed(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO class VALUES "
                    + "(10, 1, 'java.lang.Object', FALSE, NULL, NULL, NULL, NULL, 16, 0, 0),"
                    + "(11, 2, 'java.lang.String', FALSE, 10,   NULL, NULL, NULL, 24, 0, 0),"
                    + "(12, 3, 'java.lang.Thread', FALSE, 10,   NULL, NULL, NULL, 64, 0, 0)");
            st.execute("INSERT INTO instance VALUES (" + FIRST_THREAD + ", 12, 0, 0, 64, NULL, NULL), ("
                    + SECOND_THREAD + ", 12, 0, 0, 64, NULL, NULL)");
            for (int i = 0; i < STRINGS; i++) {
                long id = FIRST_STRING_ID + i;
                long holder = i < HELD_BY_THE_FIRST ? FIRST_THREAD : SECOND_THREAD;
                st.execute("INSERT INTO instance VALUES (" + id + ", 11, 0, 0, 32, NULL, NULL)");
                st.execute("INSERT INTO outbound_ref VALUES (" + holder + ", " + id + ", 0, " + i + ")");
                st.execute("INSERT INTO retained_size VALUES (" + id + ", 32)");
            }
            st.execute("INSERT INTO dump_metadata VALUES "
                    + "('fixture.hprof', 1024, 0, 8, '1.0', 0, 1024, 8, 0, FALSE, 'test', 0, FALSE)");
        }
        view = new TestHeapView(conn);
    }

    @Nested
    class PlannedToSql {

        @Test
        void isPlannedToSql() {
            assertInstanceOf(ExecutionPlan.SqlPlan.class, compile(ALL_STRINGS));
        }

        @Test
        void theFirstPageSaysMoreFollow() throws SQLException {
            OQLQueryResult first = page(ALL_STRINGS, 0);

            assertEquals(PAGE, first.results().size());
            assertTrue(first.hasMore(), "more rows follow the first page");
        }

        @Test
        void thePagesTogetherAreTheQuerysRowsNoneTwice() throws SQLException {
            assertPagesAreTheRows(ALL_STRINGS);
        }
    }

    @Nested
    class WalkedInJava {

        @Test
        void isWalkedInJava() {
            assertInstanceOf(ExecutionPlan.JavaPlan.class, compile(WHAT_THE_THREADS_HOLD));
        }

        @Test
        void theFirstPageSaysMoreFollow() throws SQLException {
            OQLQueryResult first = page(WHAT_THE_THREADS_HOLD, 0);

            assertEquals(PAGE, first.results().size());
            assertTrue(first.hasMore(), "more rows follow the first page");
        }

        @Test
        void thePagesTogetherAreTheQuerysRowsNoneTwice() throws SQLException {
            assertPagesAreTheRows(WHAT_THE_THREADS_HOLD);
        }

        /**
         * Each page hands back where the next starts: inside the first thread's five rows, then
         * past them into the second thread's; the pages read so are the query's rows, and the last
         * one hands back nothing.
         */
        @Test
        void resumesInsideAnObjectsRowsAndPastIt() throws SQLException {
            ExecutionPlan plan = compile(WHAT_THE_THREADS_HOLD);
            OQLQueryResult first = engine.execute(plan, view, PageWindow.first(PAGE));
            OQLQueryResult second = engine.execute(plan, view, PageWindow.after(first.resumeAfter(), PAGE));
            OQLQueryResult third = engine.execute(plan, view, PageWindow.after(second.resumeAfter(), PAGE));

            assertEquals(new OQLResumePoint(THREAD_CLASS, FIRST_THREAD, PAGE), first.resumeAfter());
            assertEquals(new OQLResumePoint(THREAD_CLASS, SECOND_THREAD, 1), second.resumeAfter());
            assertNull(third.resumeAfter());
            assertFalse(third.hasMore());
            List<Long> resumed = new ArrayList<>();
            for (OQLQueryResult page : List.of(first, second, third)) {
                resumed.addAll(ids(page.results()));
            }
            assertEquals(ids(page(WHAT_THE_THREADS_HOLD, 0, UNPAGED).results()), resumed);
        }

        /** A page resumed past the first thread reads it no more: gone from the heap, the page is the same. */
        @Test
        void aResumedPageReadsNothingBeforeItsPoint(Connection conn) throws SQLException {
            ExecutionPlan plan = compile(WHAT_THE_THREADS_HOLD);
            OQLQueryResult second = engine.execute(plan, view,
                    PageWindow.after(engine.execute(plan, view, PageWindow.first(PAGE)).resumeAfter(), PAGE));
            OQLResumePoint pastTheFirstThread = second.resumeAfter();
            List<Long> expected = ids(engine.execute(plan, view, PageWindow.after(pastTheFirstThread, PAGE)).results());

            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM outbound_ref WHERE source_id = " + FIRST_THREAD);
                st.execute("DELETE FROM instance WHERE instance_id = " + FIRST_THREAD);
            }

            assertEquals(expected, ids(engine.execute(plan, view, PageWindow.after(pastTheFirstThread, PAGE)).results()));
        }

        /** Sorted, the rows are read whole to sort them: it pages by offset and hands back no resume point. */
        @Test
        void aSortedQueryPagesByOffset() throws SQLException {
            assertInstanceOf(ExecutionPlan.JavaPlan.class, compile(WHAT_THE_THREADS_HOLD_SORTED));
            OQLQueryResult first = page(WHAT_THE_THREADS_HOLD_SORTED, 0);

            assertTrue(first.hasMore());
            assertNull(first.resumeAfter());
            assertPagesAreTheRows(WHAT_THE_THREADS_HOLD_SORTED);
        }
    }

    @Nested
    class AResumePoint {

        @Test
        void cannotContinueAQueryPagedByOffset() {
            ExecutionPlan plan = compile(ALL_STRINGS);

            assertThrows(IllegalArgumentException.class, () -> engine.execute(plan, view,
                    PageWindow.after(new OQLResumePoint(THREAD_CLASS, FIRST_THREAD, 0), PAGE)));
        }

        @Test
        void inAClassTheQueryDoesNotReadIsRefused() {
            ExecutionPlan plan = compile(WHAT_THE_THREADS_HOLD);

            assertThrows(IllegalArgumentException.class, () -> engine.execute(plan, view,
                    PageWindow.after(new OQLResumePoint(THREAD_CLASS + 1, FIRST_THREAD, 0), PAGE)));
        }
    }

    /** Page by page to the end: each page the next rows, the last one ending it, all of them the query's. */
    private void assertPagesAreTheRows(String oql) throws SQLException {
        OQLQueryResult unpaged = page(oql, 0, UNPAGED);
        assertEquals(STRINGS, unpaged.results().size(), unpaged::toString);
        assertFalse(unpaged.hasMore());

        List<Long> paged = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        OQLQueryResult page;
        do {
            page = page(oql, paged.size());
            assertTrue(page.isSuccess(), page::toString);
            sizes.add(page.results().size());
            page.results().forEach(row -> paged.add(row.objectId()));
        } while (page.hasMore());

        assertEquals(List.of(PAGE, PAGE, STRINGS - 2 * PAGE), sizes, "two full pages, then the last row");
        assertEquals(ids(unpaged.results()), paged);
    }

    private OQLQueryResult page(String oql, int offset) throws SQLException {
        return page(oql, offset, PAGE);
    }

    private OQLQueryResult page(String oql, int offset, int limit) throws SQLException {
        return engine.execute(compile(oql), view, new PageWindow(offset, limit));
    }

    private ExecutionPlan compile(String oql) {
        return engine.compile(engine.parse(oql));
    }

    private static List<Long> ids(List<OQLResultEntry> rows) {
        return rows.stream().map(OQLResultEntry::objectId).toList();
    }
}
