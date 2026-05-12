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

import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Conformance corpus: every example query published in
 * {@code ProfileHeapDumpOQL.vue} must parse and compile without throwing.
 * Execution semantics are covered by the focused Plan A/B/C tests; this
 * test guarantees the engine accepts the surface area the UI advertises.
 *
 * <p>If a query in the UI fails this test, either fix the engine to accept
 * it or update the example list in the Vue file — the two must stay in
 * lockstep.
 */
class ConformanceCorpusTest {

    private static final OqlEngine ENGINE = new OqlEngine();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("corpus")
    void parsesAndCompiles(String oql) {
        ExecutionPlan plan = ENGINE.compile(ENGINE.parse(oql));
        assertNotNull(plan, () -> "engine returned null plan for: " + oql);
    }

    static Stream<String> corpus() {
        return CORPUS.stream();
    }

    /**
     * Mirror of the example list in
     * {@code jeffrey-microscope/pages-microscope/src/views/profiles/detail/ProfileHeapDumpOQL.vue}.
     * Keep this list in lockstep with the Vue file.
     */
    private static final List<String> CORPUS = List.of(
            // Basics
            "SELECT * FROM java.lang.String LIMIT 50",
            "SELECT t.@displayName FROM java.lang.Thread t",
            "SELECT s.@objectId, s.@displayName FROM java.lang.String s LIMIT 50",
            // Filters & string predicates
            "SELECT s FROM java.lang.String s WHERE sizeof(s) > 1024",
            "SELECT s FROM java.lang.String s WHERE startsWith(s, \"java.\")",
            "SELECT s FROM java.lang.String s WHERE endsWith(s, \".class\")",
            "SELECT s FROM java.lang.String s WHERE contains(s, \"Exception\")",
            "SELECT s FROM java.lang.String s WHERE matchesRegex(s, \"^https?://.*\")",
            "SELECT s FROM java.lang.String s WHERE equalsIgnoreCase(s, \"OK\")",
            "SELECT s FROM java.lang.String s WHERE equalsString(s, \"java.lang.Object\")",
            "SELECT s FROM java.lang.String s WHERE isEmptyString(s)",
            // Class hierarchy
            "SELECT o FROM INSTANCEOF java.util.AbstractMap o",
            "SELECT o FROM INSTANCEOF java.lang.Throwable o",
            "SELECT o FROM IMPLEMENTS java.util.Map o",
            // Sizes & retention
            "SELECT o.@displayName, o.@retainedHeapSize FROM INSTANCEOF java.lang.Object o "
                    + "WHERE o.@retainedHeapSize > 1048576 ORDER BY o.@retainedHeapSize DESC LIMIT 20",
            "SELECT * AS RETAINED SET FROM java.util.HashMap m WHERE m.@retainedHeapSize > 10485760",
            // Aggregates
            "SELECT classof(o).name AS cls, count(*) AS n, sum(sizeof(o)) AS total "
                    + "FROM INSTANCEOF java.lang.Object o "
                    + "GROUP BY classof(o).name HAVING count(*) > 100 "
                    + "ORDER BY total DESC LIMIT 20",
            "SELECT count(*) FROM java.lang.String",
            "SELECT min(sizeof(s)), max(sizeof(s)), avg(sizeof(s)) FROM java.lang.String s",
            // Path expressions & arrays
            "SELECT s FROM java.lang.String s WHERE s.value.length > 1000",
            "SELECT m.table[0] FROM java.util.HashMap m",
            "SELECT a FROM byte[] a WHERE a.length > 10240 ORDER BY a.length DESC",
            // String accessors
            "SELECT lower(toString(s)) FROM java.lang.String s LIMIT 50",
            "SELECT substring(toString(s), 0, 80) FROM java.lang.String s WHERE stringLength(s) > 80",
            "SELECT trim(toString(s)) FROM java.lang.String s WHERE startsWith(s, \" \")",
            "SELECT upper(toString(s)) FROM java.lang.String s LIMIT 50",
            "SELECT toString(s), indexOf(toString(s), \"://\") AS pos "
                    + "FROM java.lang.String s WHERE contains(s, \"://\")",
            "SELECT toString(s), lastIndexOf(toString(s), \".\") AS pos "
                    + "FROM java.lang.String s WHERE endsWith(s, \".class\")",
            "SELECT charAt(toString(s), 0), count(*) FROM java.lang.String s "
                    + "WHERE NOT isEmptyString(s) GROUP BY charAt(toString(s), 0) "
                    + "ORDER BY count(*) DESC LIMIT 20",
            // Fuzzy text
            "SELECT toString(s), levenshtein(toString(s), \"OutOfMemoryError\") AS dist "
                    + "FROM java.lang.String s ORDER BY dist ASC LIMIT 20",
            "SELECT toString(s) FROM java.lang.String s "
                    + "WHERE jaroWinklerSimilarity(toString(s), \"java.util.HashMap\") > 0.85",
            // Wrapper toString
            "SELECT toString(i) FROM java.lang.Integer i LIMIT 50",
            "SELECT toString(b) FROM java.lang.Boolean b",
            // Numeric & control flow
            "SELECT round(avg(sizeof(s)), 2) FROM java.lang.String s",
            "SELECT CASE WHEN sizeof(o) < 64 THEN 'tiny' WHEN sizeof(o) < 1024 THEN 'small' ELSE 'big' END AS bucket, "
                    + "count(*) FROM INSTANCEOF java.lang.Object o "
                    + "GROUP BY bucket ORDER BY count(*) DESC",
            "SELECT format('class={} size={}', classof(o).name, sizeof(o)) FROM java.lang.Thread o",
            // Graph traversal
            "SELECT outbounds(t) FROM java.lang.Thread t",
            "SELECT inbounds(s) FROM java.lang.String s WHERE s.value.length > 1000",
            "SELECT reachables(t) FROM java.lang.Thread t LIMIT 1",
            "SELECT referrers(c) FROM java.lang.Class c WHERE c.@displayName LIKE \".*Cache.*\"",
            // GC roots
            "SELECT root(s) FROM java.lang.String s WHERE s.value.length > 10000",
            "SELECT root(o) FROM INSTANCEOF java.lang.Object o "
                    + "WHERE o.@retainedHeapSize > 10485760 LIMIT 5",
            "SELECT * FROM heap.roots()",
            // Dominators
            "SELECT dominatorof(o) FROM java.util.HashMap o",
            "SELECT dominators(o) FROM INSTANCEOF java.lang.Object o "
                    + "WHERE o.@retainedHeapSize > 1048576 LIMIT 20",
            // Heap helpers
            "SELECT * FROM heap.classes()",
            "SELECT * FROM heap.findClass(\"java.lang.String\")",
            "SELECT * FROM heap.findObject(0xCAFEBABE)",
            // UNION & subqueries
            "(SELECT s FROM java.lang.String s WHERE startsWith(s, \"java.\")) UNION "
                    + "(SELECT t FROM java.lang.Thread t)",
            "SELECT count(*) FROM (SELECT s FROM java.lang.String s WHERE stringLength(s) > 100)"
    );
}
