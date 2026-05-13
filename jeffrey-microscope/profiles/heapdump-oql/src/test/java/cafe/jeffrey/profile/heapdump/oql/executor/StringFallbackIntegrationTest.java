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
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompileOptions;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@link ExecutionPlan.StringFallbackPlan}: a SQL
 * pushdown for in-cap Strings paired with a Plan-C tail scan over Strings
 * whose decoded content exceeded the indexer's cap.
 */
@DuckDBTest(migration = "classpath:db/migration/heap-dump-index")
class StringFallbackIntegrationTest {

    private static final int DEFAULT_LIMIT = 100;
    private final OqlEngine engine = new OqlEngine();
    private TestHeapView view;

    @BeforeEach
    void seed(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO class VALUES "
                    + "(10, 1, 'java.lang.Object', FALSE, NULL, NULL, NULL, NULL, 16, 0, 0),"
                    + "(11, 2, 'java.lang.String', FALSE, 10,   NULL, NULL, NULL, 24, 0, 0),"
                    + "(15, 3, 'byte[]',           TRUE,  10,   NULL, NULL, NULL, 16, 0, 0)");

            // Three String instances: two with short content (in-cap) and one
            // long (exceeds cap, content=NULL).
            st.execute("INSERT INTO instance VALUES "
                    + "(100, 11, 0, 0, 32, NULL, NULL),"
                    + "(101, 11, 0, 0, 32, NULL, NULL),"
                    + "(102, 11, 0, 0, 32, NULL, NULL),"
                    + "(200, 15, 0, 2, 19, 19, 8)," // backing bytes for the long String
                    + "(201, 15, 0, 2,  6,  6, 8)," // backing bytes for short "Bearer"
                    + "(202, 15, 0, 2,  9,  9, 8)"); // backing bytes for short "foo.class"

            st.execute("INSERT INTO retained_size VALUES (100, 32), (101, 32), (102, 32)");

            // string_content: 100 in-cap with content "Bearer abcdef", 101 in-cap
            // with content "foo.class", 102 exceeds cap → content NULL.
            st.execute("INSERT INTO string_content VALUES "
                    + "(100, 13, 'Bearer abcdef'),"
                    + "(101,  9, 'foo.class'),"
                    + "(102, 65000, NULL)");

            st.execute("INSERT INTO dump_metadata VALUES "
                    + "('fixture.hprof', 1024, 0, 8, '1.0', 0, 1024, 8, 0, FALSE, 'test', 0, FALSE)");
        }
        view = new TestHeapView(conn);

        // Wire the LONG String (102) end-to-end so JavaStringDecoder (used by
        // the fallback evaluator) can resolve its content from the .hprof
        // bytes. basic_type ordinals: 2=OBJECT, 8=BYTE.
        byte[] longBytes = "Bearer this-is-a-very-long-bearer-token-that-exceeded-the-cap"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        view.setPrimitiveArrayBytes(200, longBytes);
        view.setFields(102, List.of(
                new InstanceFieldValue("value", 2, 200L),
                new InstanceFieldValue("coder", 8, (byte) 0)));
    }

    private OQLQueryResult run(String oql, boolean scanLargeStrings) throws SQLException {
        OqlStatement stmt = engine.parse(oql);
        ExecutionPlan plan = engine.compile(stmt, new OqlCompileOptions(scanLargeStrings));
        return engine.execute(plan, view, DEFAULT_LIMIT);
    }

    @Test
    void withScanLargeStringsOffOnlyInCapMatches() throws SQLException {
        OQLQueryResult r = run(
                "SELECT s FROM java.lang.String s WHERE startsWith(s, \"Bearer\")",
                false);
        // SQL pushdown finds String 100 ("Bearer abcdef"). String 102 has
        // NULL content so the SQL predicate misses it.
        assertEquals(1, r.results().size());
        assertEquals(100L, r.results().get(0).objectId());
    }

    @Test
    void withScanLargeStringsOnIncludesLongStrings() throws SQLException {
        OQLQueryResult r = run(
                "SELECT s FROM java.lang.String s WHERE startsWith(s, \"Bearer\")",
                true);
        // Plan-C tail scan resurfaces String 102 (long Bearer-prefixed content).
        // Order: SQL pushdown row first, then fallback row.
        assertEquals(2, r.results().size());
        java.util.Set<Long> ids = new java.util.HashSet<>();
        r.results().forEach(e -> ids.add(e.objectId()));
        assertTrue(ids.contains(100L));
        assertTrue(ids.contains(102L));
    }

    @Test
    void planTypeFlipsBetweenSqlPlanAndStringFallbackPlan() {
        ExecutionPlan offPlan = engine.compile(
                engine.parse("SELECT s FROM java.lang.String s WHERE startsWith(s, \"Bearer\")"),
                new OqlCompileOptions(false));
        assertInstanceOf(ExecutionPlan.SqlPlan.class, offPlan);

        ExecutionPlan onPlan = engine.compile(
                engine.parse("SELECT s FROM java.lang.String s WHERE startsWith(s, \"Bearer\")"),
                new OqlCompileOptions(true));
        assertInstanceOf(ExecutionPlan.StringFallbackPlan.class, onPlan);
    }

    @Test
    void scanLargeStringsHasNoEffectWhenNoStringContentJoin() {
        // Query has no string predicate → SqlPlan with no string_content
        // JOIN → no fallback wrapping even with scanLargeStrings=true.
        ExecutionPlan plan = engine.compile(
                engine.parse("SELECT * FROM java.lang.String LIMIT 10"),
                new OqlCompileOptions(true));
        assertInstanceOf(ExecutionPlan.SqlPlan.class, plan);
    }
}
