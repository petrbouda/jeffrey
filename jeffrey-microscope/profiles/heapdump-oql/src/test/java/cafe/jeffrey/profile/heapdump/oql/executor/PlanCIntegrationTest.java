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
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
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
 * Integration tests for Plan C: graph walks, dominator-tree lookups, path
 * expressions, string predicates against decoded fixture Strings, and the
 * generalised {@code toString} for wrapper instances. Fixture has a Thread
 * referencing two Strings, two HashMaps with a parent/child dominator
 * relationship, and a fixture String fully wired through canned fields.
 */
@DuckDBTest(migration = "classpath:db/migration/heap-dump-index")
class PlanCIntegrationTest {

    private static final int DEFAULT_LIMIT = 100;
    private static final int BASIC_TYPE_BYTE = 8;
    private final OqlEngine engine = new OqlEngine();
    private TestHeapView view;

    @BeforeEach
    void seed(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO class VALUES "
                    + "(10, 1, 'java.lang.Object',   FALSE, NULL, NULL, NULL, NULL, 16, 0, 0),"
                    + "(11, 2, 'java.lang.String',   FALSE, 10,   NULL, NULL, NULL, 24, 0, 0),"
                    + "(12, 3, 'java.lang.Thread',   FALSE, 10,   NULL, NULL, NULL, 64, 0, 0),"
                    + "(13, 4, 'java.util.HashMap',  FALSE, 10,   NULL, NULL, NULL, 48, 0, 0),"
                    + "(14, 5, 'java.lang.Integer',  FALSE, 10,   NULL, NULL, NULL, 16, 0, 0),"
                    + "(15, 6, 'byte[]',             TRUE,  10,   NULL, NULL, NULL, 16, 0, 0)");

            // record_kind: 0=instance, 1=object_array, 2=primitive_array
            st.execute("INSERT INTO instance VALUES "
                    // String s1 with backing byte[] 201, content "Hello java.lang.Foo"
                    + "(100, 11, 0, 0, 32, NULL, NULL),"
                    // String s2 with backing byte[] 202, content "foo.class"
                    + "(101, 11, 0, 0, 32, NULL, NULL),"
                    // Backing byte arrays
                    + "(201, 15, 0, 2, 35, 19, " + BASIC_TYPE_BYTE + "),"
                    + "(202, 15, 0, 2, 25,  9, " + BASIC_TYPE_BYTE + "),"
                    // Thread referencing s1 and s2
                    + "(110, 12, 0, 0, 64, NULL, NULL),"
                    // Integer
                    + "(120, 14, 0, 0, 16, NULL, NULL),"
                    // Two HashMaps with parent/child dominator
                    + "(130, 13, 0, 0, 48, NULL, NULL),"
                    + "(131, 13, 0, 0, 48, NULL, NULL)");

            // Outbound refs: Thread 110 -> s1 (100), Thread 110 -> s2 (101)
            // HashMap 130 -> HashMap 131
            st.execute("INSERT INTO outbound_ref VALUES "
                    + "(110, 100, 0, 0),"
                    + "(110, 101, 0, 1),"
                    + "(130, 131, 0, 0)");

            // Dominator: 131 is dominated by 130
            st.execute("INSERT INTO dominator VALUES (131, 130), (130, 0)");

            // Retained sizes
            st.execute("INSERT INTO retained_size VALUES (100, 32), (101, 32), "
                    + "(110, 128), (120, 16), (130, 96), (131, 48)");

            st.execute("INSERT INTO dump_metadata VALUES "
                    + "('fixture.hprof', 1024, 0, 8, '1.0', 0, 1024, 8, 0, FALSE, 'test', 0, FALSE)");
        }
        view = new TestHeapView(conn);

        // Wire String s1 (100) to byte[] 201 with content "Hello java.lang.Foo"
        // and String s2 (101) to byte[] 202 with content "foo.class".
        // basic_type ordinals: 2=OBJECT, 8=BYTE
        byte[] s1Bytes = "Hello java.lang.Foo".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] s2Bytes = "foo.class".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        view.setPrimitiveArrayBytes(201, s1Bytes);
        view.setPrimitiveArrayBytes(202, s2Bytes);
        // Java 9+ String layout: value=byte[] + coder (0=LATIN1)
        view.setFields(100, List.of(
                new InstanceFieldValue("value", 2, 201L),
                new InstanceFieldValue("coder", 8, (byte) 0)));
        view.setFields(101, List.of(
                new InstanceFieldValue("value", 2, 202L),
                new InstanceFieldValue("coder", 8, (byte) 0)));

        // Integer 120 wraps int 42
        view.setFields(120, List.of(new InstanceFieldValue("value", 10, 42)));
    }

    private OQLQueryResult run(String oql) throws SQLException {
        OqlStatement stmt = engine.parse(oql);
        ExecutionPlan plan = engine.compile(stmt);
        return engine.execute(plan, view, DEFAULT_LIMIT);
    }

    private ExecutionPlan compile(String oql) {
        return engine.compile(engine.parse(oql));
    }

    @Nested
    class StringPredicates {

        @Test
        void startsWithMatchesDecodedString() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE startsWith(s, \"Hello\")");
            assertEquals(1, r.results().size());
            assertEquals(100L, r.results().get(0).objectId());
        }

        @Test
        void endsWithMatchesDecodedString() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE endsWith(s, \".class\")");
            assertEquals(1, r.results().size());
            assertEquals(101L, r.results().get(0).objectId());
        }

        @Test
        void containsMatchesSubstring() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE contains(s, \"java.lang\")");
            assertEquals(1, r.results().size());
            assertEquals(100L, r.results().get(0).objectId());
        }

        @Test
        void matchesRegexUsesJavaPattern() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE matchesRegex(s, \"^foo\\\\..*\")");
            assertEquals(1, r.results().size());
            assertEquals(101L, r.results().get(0).objectId());
        }

        @Test
        void equalsIgnoreCaseMatches() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE equalsIgnoreCase(s, \"FOO.CLASS\")");
            assertEquals(1, r.results().size());
            assertEquals(101L, r.results().get(0).objectId());
        }

        @Test
        void isEmptyStringMatchesNothingForNonEmptyContent() throws SQLException {
            OQLQueryResult r = run("SELECT s FROM java.lang.String s WHERE isEmptyString(s)");
            assertEquals(0, r.results().size());
        }
    }

    @Nested
    class GraphTraversal {

        @Test
        void outboundsFromThreadFansOut() throws SQLException {
            OQLQueryResult r = run("SELECT outbounds(t) FROM java.lang.Thread t");
            // Thread 110 has 2 outbound refs → 2 result rows
            assertEquals(2, r.results().size());
            r.results().forEach(e -> assertNotNull(e.objectId()));
        }

        @Test
        void inboundsToStringFindsReferringThread() throws SQLException {
            OQLQueryResult r = run("SELECT inbounds(s) FROM java.lang.String s WHERE startsWith(s, \"Hello\")");
            // Only s1 (100) starts with "Hello", and it has one inbound from Thread 110.
            assertEquals(1, r.results().size());
            assertEquals(110L, r.results().get(0).objectId());
        }

        @Test
        void reachablesFollowsForwardTransitive() throws SQLException {
            OQLQueryResult r = run("SELECT reachables(t) FROM java.lang.Thread t");
            // Thread 110 → s1, s2 (and nothing onward from leafs)
            assertEquals(2, r.results().size());
        }

        @Test
        void referrersFollowsBackwardTransitive() throws SQLException {
            OQLQueryResult r = run("SELECT referrers(s) FROM java.lang.String s WHERE startsWith(s, \"Hello\")");
            // s1 ← Thread 110 (single hop)
            assertEquals(1, r.results().size());
            assertEquals(110L, r.results().get(0).objectId());
        }
    }

    @Nested
    class DominatorLookups {

        @Test
        void dominatorOfParentFromChild() throws SQLException {
            OQLQueryResult r = run("SELECT dominatorof(m) FROM java.util.HashMap m");
            // HashMap 131's dominator = 130; HashMap 130's dominator = 0 (virtual root → null)
            assertEquals(2, r.results().size());
        }

        @Test
        void dominatorsListsChildren() throws SQLException {
            OQLQueryResult r = run("SELECT dominators(m) FROM java.util.HashMap m");
            // HashMap 130 dominates 131; HashMap 131 dominates nothing.
            assertEquals(1, r.results().size());
            assertEquals(131L, r.results().get(0).objectId());
        }
    }

    @Nested
    class WrapperToString {

        @Test
        void toStringDecodesIntegerValue() throws SQLException {
            OQLQueryResult r = run("SELECT toString(i) FROM java.lang.Integer i");
            assertEquals(1, r.results().size());
            assertTrue(r.results().get(0).value().contains("42"));
        }

        @Test
        void toStringOnJavaLangStringDecodes() throws SQLException {
            OQLQueryResult r = run("SELECT toString(s) FROM java.lang.String s WHERE startsWith(s, \"Hello\")");
            assertEquals(1, r.results().size());
            assertTrue(r.results().get(0).value().contains("Hello java.lang.Foo"));
        }
    }

    @Nested
    class RetainedSet {

        @Test
        void retainedSetExpandsToDominatedDescendants() throws SQLException {
            // HashMap 130 retains 96 bytes and dominates HashMap 131 (retains 48).
            // AS RETAINED SET should return BOTH instances.
            OQLQueryResult r = run(
                    "SELECT * AS RETAINED SET FROM java.util.HashMap m WHERE m.@retainedHeapSize > 50");
            assertEquals(2, r.results().size());
            java.util.Set<Long> ids = new java.util.HashSet<>();
            r.results().forEach(e -> ids.add(e.objectId()));
            assertTrue(ids.contains(130L));
            assertTrue(ids.contains(131L));
        }

        @Test
        void retainedSetWithNoSelectionReturnsEmpty() throws SQLException {
            OQLQueryResult r = run(
                    "SELECT * AS RETAINED SET FROM java.util.HashMap m WHERE m.@retainedHeapSize > 999999");
            assertEquals(0, r.results().size());
        }
    }

    @Nested
    class ImplementsResolution {

        @Test
        void implementsAgainstStandardHprofReturnsEmpty() throws SQLException {
            // The class_interface table is empty in fixtures built from standard
            // HPROF data — IMPLEMENTS should return an empty result without
            // erroring.
            OQLQueryResult r = run("SELECT * FROM IMPLEMENTS java.util.Map o");
            assertEquals(0, r.results().size());
            assertNull(r.errorMessage());
        }

        @Test
        void implementsWithSeededInterfaceEdgeReturnsMatchingClasses(Connection conn) throws SQLException {
            // Seed an interface edge: HashMap (class 13) implements java.util.Map (class 200).
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO class VALUES "
                        + "(200, 99, 'java.util.Map', FALSE, NULL, NULL, NULL, NULL, 0, 0, 0)");
                st.execute("INSERT INTO class_interface VALUES (13, 200)");
            }
            OQLQueryResult r = run("SELECT * FROM IMPLEMENTS java.util.Map o");
            // HashMap has 2 instances in the fixture (130 and 131) — both must be returned.
            assertEquals(2, r.results().size());
        }
    }

    @Nested
    class Classification {

        @Test
        void stringPredicateRoutesToJavaPlan() {
            ExecutionPlan plan = compile("SELECT s FROM java.lang.String s WHERE startsWith(s, \"java.\")");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
        }

        @Test
        void graphFunctionRoutesToJavaPlan() {
            ExecutionPlan plan = compile("SELECT outbounds(t) FROM java.lang.Thread t");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
        }

        @Test
        void pathExpressionRoutesToJavaPlan() {
            ExecutionPlan plan = compile("SELECT s FROM java.lang.String s WHERE s.value.length > 100");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
        }

        @Test
        void retainedSizeOnPlanCFlagsDominatorTree() {
            ExecutionPlan plan = compile(
                    "SELECT s FROM java.lang.String s WHERE startsWith(s, \"x\") AND rsizeof(s) > 0");
            assertInstanceOf(ExecutionPlan.JavaPlan.class, plan);
            assertTrue(plan.needsDominatorTree());
        }
    }
}
