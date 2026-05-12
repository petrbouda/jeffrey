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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Plan A/B tests against a real DuckDB instance with the heap-dump
 * index schema applied. Each test seeds a minimal fixture: a few classes
 * (String, Thread, HashMap, AbstractMap, byte[], a Cache subclass), a handful
 * of instance rows, and {@code retained_size} entries.
 */
@DuckDBTest(migration = "classpath:db/migration/heap-dump-index")
class PlanABIntegrationTest {

    private static final int DEFAULT_LIMIT = 100;
    private final OqlEngine engine = new OqlEngine();
    private HeapView view;

    @BeforeEach
    void seed(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Classes — class_id, class_serial, name, is_array, super_class_id, classloader_id, signers_id, protection_domain_id, instance_size, static_fields_size, file_offset
            st.execute("INSERT INTO class VALUES "
                    + "(10, 1, 'java.lang.Object',   FALSE, NULL, NULL, NULL, NULL, 16,  0, 0),"
                    + "(11, 2, 'java.lang.String',   FALSE, 10,   NULL, NULL, NULL, 24,  0, 0),"
                    + "(12, 3, 'java.lang.Thread',   FALSE, 10,   NULL, NULL, NULL, 64,  0, 0),"
                    + "(13, 4, 'java.util.AbstractMap', FALSE, 10, NULL, NULL, NULL, 16, 0, 0),"
                    + "(14, 5, 'java.util.HashMap',  FALSE, 13,   NULL, NULL, NULL, 48,  0, 0),"
                    + "(15, 6, 'com.example.Cache',  FALSE, 14,   NULL, NULL, NULL, 56,  0, 0),"
                    + "(16, 7, 'byte[]',             TRUE,  10,   NULL, NULL, NULL, 16,  0, 0)");

            // Instances — instance_id, class_id, file_offset, record_kind, shallow_size, array_length, primitive_type
            // record_kind: 0=instance, 1=object_array, 2=primitive_array
            st.execute("INSERT INTO instance VALUES "
                    + "(100, 11, 0, 0,    32,   NULL, NULL),"   // String 1, small
                    + "(101, 11, 0, 0,    72,   NULL, NULL),"   // String 2, medium
                    + "(102, 11, 0, 0,  2000,   NULL, NULL),"   // String 3, large
                    + "(110, 12, 0, 0,    64,   NULL, NULL),"   // Thread 1
                    + "(120, 14, 0, 0,    48,   NULL, NULL),"   // HashMap 1
                    + "(121, 14, 0, 0,    96,   NULL, NULL),"   // HashMap 2 — larger
                    + "(130, 15, 0, 0,    56,   NULL, NULL),"   // Cache 1 (subclass of HashMap)
                    + "(140, 16, 0, 2, 16400, 16384,    8)"   // byte[16384]
            );

            // Retained sizes — instance_id, bytes
            st.execute("INSERT INTO retained_size VALUES "
                    + "(100,    32),"
                    + "(101,    72),"
                    + "(102,  2200),"
                    + "(110,  1024),"
                    + "(120,   200),"
                    + "(121, 12345),"   // big HashMap retains a lot
                    + "(130, 65000),"
                    + "(140, 16400)"
            );

            // Outbound refs — placeholder for later phases
            // GC roots — placeholder

            // Required for any HeapView.metadata() consumer (we don't use it here)
            st.execute("INSERT INTO dump_metadata VALUES "
                    + "('fixture.hprof', 1024, 0, 8, '1.0', 0, 1024, 8, 0, FALSE, 'test', 0, FALSE)");
        }
        view = new TestHeapView(conn);
    }

    private OQLQueryResult run(String oql) throws SQLException {
        OqlStatement stmt = engine.parse(oql);
        ExecutionPlan plan = engine.compile(stmt);
        return engine.execute(plan, view, DEFAULT_LIMIT);
    }

    private ExecutionPlan compile(String oql) {
        return engine.compile(engine.parse(oql));
    }

    // ---------- Plan A — pure SQL ----------

    @Nested
    class PlanA {

        @Test
        void simpleClassFilter() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.lang.String");
            assertNull(r.errorMessage(), () -> "unexpected error: " + r.errorMessage());
            assertEquals(3, r.results().size());
            r.results().forEach(e -> assertEquals("java.lang.String", e.className()));
            r.results().forEach(e -> assertNotNull(e.objectId()));
        }

        @Test
        void shallowSizeFilter() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.lang.String s WHERE sizeof(s) > 100");
            assertEquals(1, r.results().size());
            assertEquals(102L, r.results().get(0).objectId());
            assertEquals(2000, r.results().get(0).size());
        }

        @Test
        void usedHeapSizeAttr() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.lang.String s WHERE s.@usedHeapSize > 100");
            assertEquals(1, r.results().size());
            assertEquals(102L, r.results().get(0).objectId());
        }

        @Test
        void retainedHeapSizeJoinAndOrder() throws SQLException {
            OQLQueryResult r = run(
                    "SELECT * FROM java.util.HashMap m WHERE m.@retainedHeapSize > 1000 ORDER BY m.@retainedHeapSize DESC");
            assertEquals(1, r.results().size());
            assertEquals(121L, r.results().get(0).objectId());
            assertEquals(12345L, r.results().get(0).retainedSize());
        }

        @Test
        void countStarAggregate() throws SQLException {
            OQLQueryResult r = run("SELECT count(*) FROM java.lang.String");
            assertEquals(1, r.results().size());
            // SELECT count(*) → value rendered as "count(star)=3" — assert it carries 3.
            assertTrue(r.results().get(0).value().contains("3"));
        }

        @Test
        void groupByHavingOrderByLimit() throws SQLException {
            // Aggregate all instances grouped by class; expect ordering by total size desc.
            OQLQueryResult r = run(
                    "SELECT classof(o).name, count(*) AS n, sum(sizeof(o)) AS total "
                            + "FROM java.lang.String o "
                            + "GROUP BY classof(o).name "
                            + "HAVING count(*) > 1 "
                            + "ORDER BY total DESC");
            assertEquals(1, r.results().size());
            assertEquals("java.lang.String", r.results().get(0).className());
        }

        @Test
        void minMaxAvg() throws SQLException {
            OQLQueryResult r = run("SELECT min(sizeof(s)), max(sizeof(s)), avg(sizeof(s)) FROM java.lang.String s");
            assertEquals(1, r.results().size());
        }

        @Test
        void arrayLengthFilter() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM byte[] a WHERE a.length > 10000");
            assertEquals(1, r.results().size());
            assertEquals(140L, r.results().get(0).objectId());
        }

        @Test
        void unionAll() throws SQLException {
            OQLQueryResult r = run(
                    "(SELECT * FROM java.lang.Thread t) UNION (SELECT * FROM com.example.Cache c)");
            assertEquals(2, r.results().size());
        }

        @Test
        void distinct() throws SQLException {
            OQLQueryResult r = run("SELECT DISTINCT classof(s).name FROM java.lang.String s");
            assertEquals(1, r.results().size());
            assertEquals("java.lang.String", r.results().get(0).className());
        }

        @Test
        void explicitLimitAndOffset() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.lang.String LIMIT 2 OFFSET 1");
            assertEquals(2, r.results().size());
        }

        @Test
        void caseExpression() throws SQLException {
            OQLQueryResult r = run(
                    "SELECT CASE WHEN sizeof(s) < 64 THEN 'tiny' WHEN sizeof(s) < 1024 THEN 'small' ELSE 'big' END "
                            + "FROM java.lang.String s");
            assertEquals(3, r.results().size());
        }

        @Test
        void numericPassthroughAndArithmetic() throws SQLException {
            OQLQueryResult r = run("SELECT abs(sizeof(s) - 100), round(sizeof(s) / 2.0, 1) FROM java.lang.String s");
            assertEquals(3, r.results().size());
        }
    }

    // ---------- Plan B — pre-pass + SQL ----------

    @Nested
    class PlanB {

        @Test
        void instanceOfWalksHierarchy() throws SQLException {
            // INSTANCEOF java.util.AbstractMap → matches AbstractMap, HashMap, Cache.
            OQLQueryResult r = run("SELECT * FROM INSTANCEOF java.util.AbstractMap o");
            // HashMap: 2 instances + Cache: 1 instance → 3 rows.
            assertEquals(3, r.results().size());
        }

        @Test
        void instanceOfWithRetainedSizeFilter() throws SQLException {
            OQLQueryResult r = run(
                    "SELECT * FROM INSTANCEOF java.util.AbstractMap o WHERE o.@retainedHeapSize > 10000 ORDER BY o.@retainedHeapSize DESC");
            assertEquals(2, r.results().size());
            // Cache 130 retains 65000, HashMap 121 retains 12345 — order by desc.
            assertEquals(130L, r.results().get(0).objectId());
            assertEquals(121L, r.results().get(1).objectId());
        }

        @Test
        void instanceOfRoot() throws SQLException {
            // INSTANCEOF java.lang.Object should return everything.
            OQLQueryResult r = run("SELECT * FROM INSTANCEOF java.lang.Object o");
            assertEquals(8, r.results().size());
        }

        @Test
        void instanceOfWithCountAggregate() throws SQLException {
            OQLQueryResult r = run("SELECT count(*) FROM INSTANCEOF java.util.AbstractMap o");
            assertEquals(1, r.results().size());
            assertTrue(r.results().get(0).value().contains("3"));
        }

        @Test
        void instanceOfMissingClassReturnsEmpty() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM INSTANCEOF com.nonexistent.Type o");
            assertEquals(0, r.results().size());
        }
    }

    // ---------- Plans + error surfacing ----------

    @Nested
    class Plans {

        @Test
        void planAClassifiesAsSqlPlan() {
            ExecutionPlan plan = compile("SELECT * FROM java.lang.String");
            assertInstanceOf(ExecutionPlan.SqlPlan.class, plan);
        }

        @Test
        void planBClassifiesAsHybrid() {
            ExecutionPlan plan = compile("SELECT * FROM INSTANCEOF java.lang.Object o");
            assertInstanceOf(ExecutionPlan.HybridPlan.class, plan);
        }

        @Test
        void retainedJoinTriggersDominatorTreeFlag() {
            ExecutionPlan plan = compile("SELECT * FROM java.util.HashMap m WHERE rsizeof(m) > 1000");
            assertTrue(plan.needsDominatorTree());
        }

        @Test
        void plainQueryDoesNotNeedDominatorTree() {
            ExecutionPlan plan = compile("SELECT * FROM java.lang.String");
            assertEquals(false, plan.needsDominatorTree());
        }

        @Test
        void pathExpressionFallsToJavaPlan() {
            // s.value.length is Plan C territory until Phase 3.
            ExecutionPlan plan = compile("SELECT s FROM java.lang.String s WHERE s.value.length > 100");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
        }

        @Test
        void graphFunctionFallsToJavaPlan() {
            ExecutionPlan plan = compile("SELECT referrers(t) FROM java.lang.Thread t");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
        }

        @Test
        void asRetainedSetCompilesToRetainedSetPlan() {
            ExecutionPlan plan = compile("SELECT * AS RETAINED SET FROM java.util.HashMap m");
            assertInstanceOf(ExecutionPlan.RetainedSetPlan.class, plan);
            assertTrue(plan.needsDominatorTree());
        }
    }

    // ---------- Result shape sanity ----------

    @Nested
    class ResultShape {

        @Test
        void selectStarPopulatesInstanceIdClassNameAndSize() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.lang.String");
            List<OQLResultEntry> rows = r.results();
            assertEquals(3, rows.size());
            for (OQLResultEntry e : rows) {
                assertNotNull(e.objectId());
                assertEquals("java.lang.String", e.className());
                assertTrue(e.size() > 0);
            }
        }

        @Test
        void retainedSizeColumnIsPopulatedWhenRequested() throws SQLException {
            OQLQueryResult r = run("SELECT * FROM java.util.HashMap m WHERE m.@retainedHeapSize > 0");
            r.results().forEach(e -> assertNotNull(e.retainedSize()));
        }

        @Test
        void aggregateRowHasNullObjectId() throws SQLException {
            OQLQueryResult r = run("SELECT count(*) FROM java.lang.String");
            assertEquals(1, r.results().size());
            assertNull(r.results().get(0).objectId());
        }
    }
}
