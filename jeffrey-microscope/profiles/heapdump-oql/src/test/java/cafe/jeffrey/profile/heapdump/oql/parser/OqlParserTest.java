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
package cafe.jeffrey.profile.heapdump.oql.parser;

import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.ast.BinaryOperator;
import cafe.jeffrey.profile.heapdump.oql.ast.FromClause;
import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.UnionQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.SelectModifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser-level conformance tests. Verifies that every keyword, operator,
 * function, and clause documented in the OQL spec parses into the expected
 * AST shape — and that error cases produce {@link OqlParseException} with a
 * useful message.
 */
class OqlParserTest {

    private final OqlEngine engine = new OqlEngine();

    private OqlQuery parseSingle(String oql) {
        OqlStatement stmt = engine.parse(oql);
        return assertInstanceOf(OqlQuery.class, stmt);
    }

    @Nested
    class Basics {

        @Test
        void simpleSelectStarFrom() {
            OqlQuery q = parseSingle("SELECT * FROM java.lang.String");
            assertEquals(1, q.select().projections().size());
            assertTrue(q.select().projections().get(0).star());
            ObjectSource.ClassSource cs = assertInstanceOf(
                    ObjectSource.ClassSource.class, q.from().source());
            assertEquals("java.lang.String", cs.className());
            assertEquals(FromClause.FromKind.NONE, q.from().kind());
        }

        @Test
        void selectWithAlias() {
            OqlQuery q = parseSingle("SELECT s FROM java.lang.String s");
            assertEquals("s", q.from().alias());
            OqlExpr.BindingRef ref = assertInstanceOf(
                    OqlExpr.BindingRef.class, q.select().projections().get(0).expr());
            assertEquals("s", ref.name());
        }

        @Test
        void caseInsensitiveKeywords() {
            // Same statement in mixed case
            OqlQuery upper = parseSingle("SELECT * FROM java.lang.String");
            OqlQuery lower = parseSingle("select * from java.lang.String");
            OqlQuery mixed = parseSingle("Select * From java.lang.String");
            assertEquals(upper.from().source(), lower.from().source());
            assertEquals(upper.from().source(), mixed.from().source());
        }

        @Test
        void arrayClass() {
            OqlQuery q = parseSingle("SELECT a FROM byte[] a WHERE a.length > 1024");
            ObjectSource.ClassSource cs = assertInstanceOf(
                    ObjectSource.ClassSource.class, q.from().source());
            assertEquals("byte[]", cs.className());
        }

        @Test
        void multiDimArrayClass() {
            OqlQuery q = parseSingle("SELECT * FROM java.lang.Object[][]");
            ObjectSource.ClassSource cs = assertInstanceOf(
                    ObjectSource.ClassSource.class, q.from().source());
            assertEquals("java.lang.Object[][]", cs.className());
        }
    }

    @Nested
    class Hierarchies {

        @Test
        void instanceOfFrom() {
            OqlQuery q = parseSingle("SELECT o FROM INSTANCEOF java.util.AbstractMap o");
            assertEquals(FromClause.FromKind.INSTANCEOF, q.from().kind());
        }

        @Test
        void implementsFrom() {
            OqlQuery q = parseSingle("SELECT o FROM IMPLEMENTS java.util.Map o");
            assertEquals(FromClause.FromKind.IMPLEMENTS, q.from().kind());
        }
    }

    @Nested
    class Predicates {

        @Test
        void numericComparison() {
            OqlQuery q = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE sizeof(s) > 1024");
            OqlExpr.BinaryOp where = assertInstanceOf(OqlExpr.BinaryOp.class, q.whereExpr());
            assertEquals(BinaryOperator.GT, where.op());
        }

        @Test
        void likeRegex() {
            OqlQuery q = parseSingle(
                    "SELECT c FROM java.lang.Class c WHERE c.@displayName LIKE \".*Cache.*\"");
            OqlExpr.BinaryOp where = assertInstanceOf(OqlExpr.BinaryOp.class, q.whereExpr());
            assertEquals(BinaryOperator.LIKE, where.op());
        }

        @Test
        void andOrNotPrecedence() {
            OqlQuery q = parseSingle(
                    "SELECT s FROM java.lang.String s "
                            + "WHERE NOT sizeof(s) > 1024 AND startsWith(s, 'java.') OR endsWith(s, '.class')");
            // Top should be OR, then AND with NOT under the AND's left.
            OqlExpr.BinaryOp or = assertInstanceOf(OqlExpr.BinaryOp.class, q.whereExpr());
            assertEquals(BinaryOperator.OR, or.op());
            OqlExpr.BinaryOp and = assertInstanceOf(OqlExpr.BinaryOp.class, or.left());
            assertEquals(BinaryOperator.AND, and.op());
            assertInstanceOf(OqlExpr.UnaryOp.class, and.left());
        }

        @Test
        void inAndNotIn() {
            OqlQuery in = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE sizeof(s) IN (16, 24, 32)");
            assertInstanceOf(OqlExpr.InOp.class, in.whereExpr());
            assertEquals(false, ((OqlExpr.InOp) in.whereExpr()).negate());

            OqlQuery notIn = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE sizeof(s) NOT IN (16, 24, 32)");
            assertEquals(true, ((OqlExpr.InOp) notIn.whereExpr()).negate());
        }

        @Test
        void isNullAndIsNotNull() {
            OqlQuery q1 = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE s.value IS NULL");
            OqlExpr.NullCheck nc1 = assertInstanceOf(OqlExpr.NullCheck.class, q1.whereExpr());
            assertEquals(false, nc1.negate());

            OqlQuery q2 = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE s.value IS NOT NULL");
            OqlExpr.NullCheck nc2 = assertInstanceOf(OqlExpr.NullCheck.class, q2.whereExpr());
            assertEquals(true, nc2.negate());
        }
    }

    @Nested
    class Paths {

        @Test
        void singleFieldAccess() {
            OqlQuery q = parseSingle(
                    "SELECT s.value FROM java.lang.String s");
            OqlExpr.PathExpr path = assertInstanceOf(
                    OqlExpr.PathExpr.class, q.select().projections().get(0).expr());
            assertEquals(1, path.segments().size());
            PathSegment.Field f = assertInstanceOf(PathSegment.Field.class, path.segments().get(0));
            assertEquals("value", f.name());
        }

        @Test
        void chainedFieldAndArrayLength() {
            OqlQuery q = parseSingle(
                    "SELECT s FROM java.lang.String s WHERE s.value.length > 100");
            OqlExpr.BinaryOp where = assertInstanceOf(OqlExpr.BinaryOp.class, q.whereExpr());
            OqlExpr.PathExpr lhs = assertInstanceOf(OqlExpr.PathExpr.class, where.left());
            assertEquals(2, lhs.segments().size());
        }

        @Test
        void arrayIndexAccess() {
            OqlQuery q = parseSingle(
                    "SELECT m.table[0] FROM java.util.HashMap m");
            OqlExpr.PathExpr p = assertInstanceOf(
                    OqlExpr.PathExpr.class, q.select().projections().get(0).expr());
            assertEquals(2, p.segments().size());
            assertInstanceOf(PathSegment.Field.class, p.segments().get(0));
            assertInstanceOf(PathSegment.Index.class, p.segments().get(1));
        }

        @Test
        void mixedDotAndIndexSegments() {
            OqlQuery q = parseSingle(
                    "SELECT m.table[3].key FROM java.util.HashMap m WHERE 1 = 1");
            OqlExpr.PathExpr p = assertInstanceOf(
                    OqlExpr.PathExpr.class, q.select().projections().get(0).expr());
            assertEquals(3, p.segments().size());
            assertInstanceOf(PathSegment.Field.class, p.segments().get(0));
            assertInstanceOf(PathSegment.Index.class, p.segments().get(1));
            assertInstanceOf(PathSegment.Field.class, p.segments().get(2));
        }
    }

    @Nested
    class Functions {

        @Test
        void simpleFunctionCalls() {
            assertNotNull(engine.parse(
                    "SELECT sizeof(o) FROM INSTANCEOF java.lang.Object o"));
            assertNotNull(engine.parse(
                    "SELECT rsizeof(o) FROM INSTANCEOF java.lang.Object o"));
            assertNotNull(engine.parse(
                    "SELECT classof(o) FROM INSTANCEOF java.lang.Object o"));
            assertNotNull(engine.parse(
                    "SELECT toString(s) FROM java.lang.String s"));
        }

        @Test
        void heapHelpers() {
            assertNotNull(engine.parse("SELECT * FROM heap.classes()"));
            assertNotNull(engine.parse("SELECT * FROM heap.roots()"));
            assertNotNull(engine.parse("SELECT * FROM heap.findClass(\"java.lang.String\")"));
        }

        @Test
        void stringPredicates() {
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE startsWith(s, \"java.\")"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE endsWith(s, \".class\")"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE contains(s, \"Exception\")"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE matchesRegex(s, \"^https?://.*\")"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE equalsIgnoreCase(s, \"OK\")"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s WHERE isEmptyString(s)"));
        }

        @Test
        void stringAccessors() {
            assertNotNull(engine.parse(
                    "SELECT lower(toString(s)) FROM java.lang.String s"));
            assertNotNull(engine.parse(
                    "SELECT substring(toString(s), 0, 80) FROM java.lang.String s"));
            assertNotNull(engine.parse(
                    "SELECT indexOf(toString(s), \"://\") FROM java.lang.String s"));
        }

        @Test
        void fuzzyText() {
            assertNotNull(engine.parse(
                    "SELECT levenshtein(toString(s), \"OutOfMemoryError\") FROM java.lang.String s"));
            assertNotNull(engine.parse(
                    "SELECT s FROM java.lang.String s "
                            + "WHERE jaroWinklerSimilarity(toString(s), \"java.util.HashMap\") > 0.85"));
        }

        @Test
        void numericAndControlFlow() {
            assertNotNull(engine.parse(
                    "SELECT abs(sizeof(s) - 1024) FROM java.lang.String s"));
            assertNotNull(engine.parse(
                    "SELECT coalesce(toString(s), '<null>') FROM java.lang.String s"));
            assertNotNull(engine.parse(
                    "SELECT least(sizeof(s), 65536) FROM java.lang.String s"));
        }

        @Test
        void caseExpression() {
            OqlQuery q = parseSingle(
                    "SELECT CASE WHEN sizeof(s) < 64 THEN 'tiny' "
                            + "WHEN sizeof(s) < 1024 THEN 'small' "
                            + "ELSE 'big' END "
                            + "FROM java.lang.String s");
            OqlExpr.CaseExpr ce = assertInstanceOf(
                    OqlExpr.CaseExpr.class, q.select().projections().get(0).expr());
            assertEquals(2, ce.whens().size());
            assertNotNull(ce.elseExpr());
        }
    }

    @Nested
    class AggregatesAndClauses {

        @Test
        void countStar() {
            OqlQuery q = parseSingle("SELECT count(*) FROM java.lang.String");
            OqlExpr.FunctionCall f = assertInstanceOf(
                    OqlExpr.FunctionCall.class, q.select().projections().get(0).expr());
            assertEquals("count", f.name());
            assertTrue(f.star());
        }

        @Test
        void groupByHavingOrderByLimit() {
            OqlQuery q = parseSingle(
                    "SELECT classof(o).name, count(*) AS n "
                            + "FROM INSTANCEOF java.lang.Object o "
                            + "GROUP BY classof(o).name "
                            + "HAVING count(*) > 100 "
                            + "ORDER BY n DESC "
                            + "LIMIT 20");
            assertEquals(1, q.groupBy().size());
            assertNotNull(q.having());
            assertEquals(1, q.orderBy().size());
            assertTrue(q.orderBy().get(0).descending());
            assertNotNull(q.limit());
            assertEquals(20, q.limit().limit());
        }

        @Test
        void limitWithOffset() {
            OqlQuery q = parseSingle(
                    "SELECT * FROM java.lang.String LIMIT 50 OFFSET 100");
            assertEquals(50, q.limit().limit());
            assertEquals(100, q.limit().offset());
        }

        @Test
        void distinct() {
            OqlQuery q = parseSingle(
                    "SELECT DISTINCT classof(s).name FROM java.lang.String s");
            assertEquals(SelectModifier.DISTINCT, q.select().modifier());
        }

        @Test
        void asRetainedSet() {
            OqlQuery q = parseSingle(
                    "SELECT * AS RETAINED SET FROM java.util.HashMap m WHERE m.@retainedHeapSize > 10485760");
            assertEquals(SelectModifier.AS_RETAINED_SET, q.select().modifier());
        }
    }

    @Nested
    class Attributes {

        @Test
        void retainedHeapSizeAttr() {
            OqlQuery q = parseSingle(
                    "SELECT o.@retainedHeapSize FROM INSTANCEOF java.lang.Object o");
            OqlExpr.PathExpr p = assertInstanceOf(
                    OqlExpr.PathExpr.class, q.select().projections().get(0).expr());
            assertEquals(1, p.segments().size());
            PathSegment.AttrField attr = assertInstanceOf(
                    PathSegment.AttrField.class, p.segments().get(0));
            assertEquals("retainedHeapSize", attr.name());
        }

        @Test
        void standaloneAttrRef() {
            // @retainedHeapSize used as a primary (without a path prefix) —
            // valid inside an aggregate, for example.
            OqlQuery q = parseSingle(
                    "SELECT @retainedHeapSize FROM java.lang.String");
            OqlExpr.AttrRef a = assertInstanceOf(
                    OqlExpr.AttrRef.class, q.select().projections().get(0).expr());
            assertEquals("retainedHeapSize", a.name());
        }
    }

    @Nested
    class Literals {

        @Test
        void intDecimalHexStringBool() {
            assertNotNull(engine.parse(
                    "SELECT 100, 3.14, 0xCAFEBABE, 'hello', true, false, null "
                            + "FROM java.lang.String"));
        }

        @Test
        void hexAddressInHelper() {
            OqlQuery q = parseSingle("SELECT heap.findObject(0xCAFEBABE) FROM java.lang.String");
            OqlExpr.FunctionCall fc = assertInstanceOf(
                    OqlExpr.FunctionCall.class, q.select().projections().get(0).expr());
            assertEquals("heap.findObject", fc.name());
            OqlExpr.Literal arg = assertInstanceOf(OqlExpr.Literal.class, fc.args().get(0));
            assertEquals(0xCAFEBABEL, ((Long) arg.value()).longValue());
        }
    }

    @Nested
    class Unions {

        @Test
        void simpleUnion() {
            OqlStatement stmt = engine.parse(
                    "(SELECT s FROM java.lang.String s) UNION (SELECT t FROM java.lang.Thread t)");
            UnionQuery union = assertInstanceOf(UnionQuery.class, stmt);
            assertEquals(2, union.branches().size());
        }
    }

    @Nested
    class Subqueries {

        @Test
        void subqueryInFrom() {
            OqlQuery q = parseSingle(
                    "SELECT classof(o).name FROM "
                            + "(SELECT * FROM INSTANCEOF java.util.AbstractMap o WHERE o.@retainedHeapSize > 524288) o");
            assertInstanceOf(ObjectSource.SubquerySource.class, q.from().source());
        }
    }

    @Nested
    class ErrorPaths {

        @Test
        void emptyInputThrows() {
            assertThrows(OqlParseException.class, () -> engine.parse(""));
            assertThrows(OqlParseException.class, () -> engine.parse("   "));
            assertThrows(OqlParseException.class, () -> engine.parse(null));
        }

        @Test
        void syntaxErrorReportsLocation() {
            OqlParseException ex = assertThrows(
                    OqlParseException.class,
                    () -> engine.parse("SELECT FROM"));
            assertTrue(ex.line() >= 1);
        }

        @Test
        void unknownAttributeIsRejected() {
            OqlParseException ex = assertThrows(
                    OqlParseException.class,
                    () -> engine.parse("SELECT s.@nonexistent FROM java.lang.String s"));
            assertTrue(ex.getMessage().contains("Unknown attribute"));
        }

        @Test
        void unknownFunctionIsRejected() {
            OqlParseException ex = assertThrows(
                    OqlParseException.class,
                    () -> engine.parse("SELECT madeUpFunction(s) FROM java.lang.String s"));
            assertTrue(ex.getMessage().contains("Unknown function"));
        }

        @Test
        void unknownHeapHelperIsRejected() {
            OqlParseException ex = assertThrows(
                    OqlParseException.class,
                    () -> engine.parse("SELECT * FROM heap.bogus()"));
            assertTrue(ex.getMessage().contains("Unknown heap helper"));
        }

        @Test
        void starArgRejectedForNonAggregate() {
            assertThrows(
                    OqlParseException.class,
                    () -> engine.parse("SELECT sizeof(*) FROM java.lang.String"));
        }
    }
}
