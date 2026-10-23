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
import cafe.jeffrey.profile.heapdump.oql.function.GraphWalkFunctions;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transitive walks -- {@code reachables}, {@code referrers} and {@code AS RETAINED SET} -- over a
 * graph shaped to catch what a breadth-first walk gets wrong: a cycle, an id that is referenced but
 * is no instance (a class object holding a static), and seeds that dominate each other.
 *
 * <pre>
 *   references:  1 → 2 → 3 → 1 (cycle),  3 → 4,  1 → 999 → 5   (999 is a class, not an instance)
 *   dominators:  1 ← 2 ← 3 ← 4,  5 under the virtual root
 * </pre>
 */
@DuckDBTest(migration = "classpath:db/migration/heap-dump-index")
class TransitiveWalksTest {

    private static final int DEFAULT_LIMIT = 100;
    private static final long CLASS_OBJECT = 999L;

    private TestHeapView view;

    @BeforeEach
    void seed(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO class VALUES "
                    + "(10, 1, 'java.lang.Object', FALSE, NULL, NULL, NULL, NULL, 16, 0, 0),"
                    + "(12, 2, 'Node',             FALSE, 10,   NULL, NULL, NULL, 24, 0, 0)");
            st.execute("INSERT INTO instance VALUES "
                    + "(1, 12, 0, 0, 24, NULL, NULL),"
                    + "(2, 12, 0, 0, 24, NULL, NULL),"
                    + "(3, 12, 0, 0, 24, NULL, NULL),"
                    + "(4, 12, 0, 0, 24, NULL, NULL),"
                    + "(5, 12, 0, 0, 24, NULL, NULL)");
            st.execute("INSERT INTO outbound_ref VALUES "
                    + "(1, 2, 0, 0), (2, 3, 0, 0), (3, 1, 0, 0), (3, 4, 0, 1),"
                    + "(1, " + CLASS_OBJECT + ", 0, 1), (" + CLASS_OBJECT + ", 5, 0, 0)");
            st.execute("INSERT INTO dominator VALUES (1, 0), (2, 1), (3, 2), (4, 3), (5, 0)");
            st.execute("INSERT INTO retained_size VALUES (1, 96), (2, 72), (3, 48), (4, 24), (5, 24)");
        }
        view = new TestHeapView(conn);
    }

    @Nested
    @DisplayName("reachables")
    class Reachables {

        @Test
        @DisplayName("answers every instance onward once, nearest first, through the cycle and the class object")
        void walksForwardNearestFirst() throws SQLException {
            assertEquals(List.of(2L, 3L, 5L, 4L), ids(GraphWalkFunctions.reachables(view, 1L)));
        }

        @Test
        @DisplayName("never answers the start, even when the cycle leads back to it")
        void leavesOutTheStart() throws SQLException {
            assertFalse(ids(GraphWalkFunctions.reachables(view, 2L)).contains(2L));
        }
    }

    @Nested
    @DisplayName("referrers")
    class Referrers {

        @Test
        @DisplayName("answers every instance that leads to it, nearest first")
        void walksBackwardNearestFirst() throws SQLException {
            assertEquals(List.of(3L, 2L, 1L), ids(GraphWalkFunctions.referrers(view, 4L)));
        }

        @Test
        @DisplayName("walks through an id that is no instance without answering it")
        void walksThroughAClassObject() throws SQLException {
            assertEquals(List.of(1L, 3L, 2L), ids(GraphWalkFunctions.referrers(view, 5L)));
        }
    }

    @Nested
    @DisplayName("AS RETAINED SET")
    class RetainedSet {

        @Test
        @DisplayName("expands seeds that dominate each other once each, seeds first")
        void expandsNestedSeedsOnce() throws SQLException {
            OQLQueryResult result = RetainedSetExpander.expand(seeds(1L, 3L), view, DEFAULT_LIMIT);

            assertEquals(List.of(1L, 3L, 2L, 4L),
                    result.results().stream().map(OQLResultEntry::objectId).toList());
        }

        @Test
        @DisplayName("says there is more when the expansion outgrows the limit")
        void reportsMoreBeyondTheLimit() throws SQLException {
            OQLQueryResult result = RetainedSetExpander.expand(seeds(1L), view, 2);

            assertEquals(2, result.results().size());
            assertTrue(result.hasMore());
        }

        @Test
        @DisplayName("answers nothing for no seeds")
        void answersNothingWithoutSeeds() throws SQLException {
            assertTrue(RetainedSetExpander.expand(seeds(), view, DEFAULT_LIMIT).results().isEmpty());
        }
    }

    private static List<Long> ids(List<InstanceRow> rows) {
        return rows.stream().map(InstanceRow::instanceId).toList();
    }

    private static OQLQueryResult seeds(long... ids) {
        List<OQLResultEntry> entries = Arrays.stream(ids)
                .mapToObj(id -> new OQLResultEntry(id, "Node", "Node@" + id, 24, null))
                .toList();
        return OQLQueryResult.success(entries, entries.size(), false, 0);
    }
}
