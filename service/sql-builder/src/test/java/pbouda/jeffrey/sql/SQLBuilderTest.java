/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.sql;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pbouda.jeffrey.sql.SQLBuilder.*;

class SQLBuilderTest {

    private static final String PROFILE_ID = "test-profile";

    @Nested
    class BasicSelect {

        @Test
        void selectAllColumns() {
            String sql = SQLBuilder.select()
                    .from("events")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("SELECT *"));
            assertTrue(sql.contains("FROM events"));
        }

        @Test
        void selectSpecificColumns() {
            String sql = SQLBuilder.select("id", "name", "value")
                    .from("events")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("SELECT id, name, value"));
        }

        @Test
        void selectWithTableAlias() {
            String sql = SQLBuilder.select("e.id", "e.name")
                    .from("events", "e")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("FROM events e"));
        }
    }

    @Nested
    class WhereConditions {

        @Test
        void simpleEqualsCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where("status", "=", l("active"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE status = 'active'"));
        }

        @Test
        void numericCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where("count", ">", l(100))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE count > 100"));
        }

        @Test
        void multipleAndConditions() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where("status", "=", l("active"))
                    .and("count", ">", l(10))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE status = 'active' AND count > 10"));
        }

        @Test
        void orCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where("status", "=", l("active"))
                    .or("status", "=", l("pending"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE status = 'active' OR status = 'pending'"));
        }

        @Test
        void conditionWithStaticFactories() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(eq("id", l(1)))
                    .and(gt("count", l(5)))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE id = 1 AND count > 5"));
        }

        @Test
        void likeCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(like("name", "%test%"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE name LIKE '%test%'"));
        }

        @Test
        void inConditionWithStrings() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(in("status", "active", "pending", "completed"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE status IN ('active', 'pending', 'completed')"));
        }

        @Test
        void inConditionWithIntegers() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(inInts("id", 1, 2, 3))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE id IN (1, 2, 3)"));
        }
    }

    @Nested
    class JoinClauses {

        @Test
        void innerJoin() {
            String sql = SQLBuilder.select("e.*", "t.name")
                    .from("events", "e")
                    .join("threads t", "e.thread_id = t.id")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("INNER JOIN threads t ON e.thread_id = t.id"));
        }

        @Test
        void leftJoin() {
            String sql = SQLBuilder.select("e.*")
                    .from("events", "e")
                    .leftJoin("frames f", "e.frame_id = f.id")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("LEFT JOIN frames f ON e.frame_id = f.id"));
        }

        @Test
        void rightJoin() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .rightJoin("threads t", "e.thread_id = t.id")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("RIGHT JOIN threads t ON e.thread_id = t.id"));
        }

        @Test
        void multipleJoins() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .join("threads t", "e.thread_id = t.id")
                    .leftJoin("frames f", "e.frame_id = f.id")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("INNER JOIN threads t ON e.thread_id = t.id"));
            assertTrue(sql.contains("LEFT JOIN frames f ON e.frame_id = f.id"));
        }

        @Test
        void joinWithConditionObject() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .join("threads t", eq("e.thread_id", c("t.id")))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("INNER JOIN threads t ON e.thread_id = t.id"));
        }
    }

    @Nested
    class GroupByAndHaving {

        @Test
        void groupBySingleColumn() {
            String sql = SQLBuilder.select("status", "COUNT(*)")
                    .from("events")
                    .groupBy("status")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("GROUP BY status"));
        }

        @Test
        void groupByMultipleColumns() {
            String sql = SQLBuilder.select("status", "type", "COUNT(*)")
                    .from("events")
                    .groupBy("status", "type")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("GROUP BY status, type"));
        }

        @Test
        void havingCondition() {
            String sql = SQLBuilder.select("status", "COUNT(*) as cnt")
                    .from("events")
                    .groupBy("status")
                    .having("cnt", ">", l(5))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("HAVING cnt > 5"));
        }
    }

    @Nested
    class OrderBy {

        @Test
        void orderBySingleColumn() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .orderBy("created_at")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("ORDER BY created_at"));
        }

        @Test
        void orderByWithDirection() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .orderBy("created_at", "DESC")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("ORDER BY created_at DESC"));
        }

        @Test
        void orderByMultipleColumns() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .orderBy("status")
                    .orderBy("created_at", "DESC")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("ORDER BY status, created_at DESC"));
        }
    }

    @Nested
    class ColumnManagement {

        @Test
        void addColumn() {
            SQLBuilder builder = SQLBuilder.select("id")
                    .addColumn("name");

            List<String> columns = builder.getColumns();
            assertEquals(2, columns.size());
            assertTrue(columns.contains("id"));
            assertTrue(columns.contains("name"));
        }

        @Test
        void addMultipleColumns() {
            SQLBuilder builder = SQLBuilder.select()
                    .addColumns("id", "name", "status");

            assertEquals(3, builder.getColumns().size());
        }

        @Test
        void addColumnsFromList() {
            SQLBuilder builder = SQLBuilder.select()
                    .addColumns(List.of("id", "name"));

            assertEquals(2, builder.getColumns().size());
        }

        @Test
        void clearColumns() {
            SQLBuilder builder = SQLBuilder.select("id", "name")
                    .clearColumns()
                    .addColumn("status");

            assertEquals(1, builder.getColumns().size());
            assertTrue(builder.getColumns().contains("status"));
        }

        @Test
        void hasColumns() {
            SQLBuilder empty = SQLBuilder.select();
            SQLBuilder withColumns = SQLBuilder.select("id");

            assertFalse(empty.hasColumns());
            assertTrue(withColumns.hasColumns());
        }

        @Test
        void addNullColumnThrowsException() {
            SQLBuilder builder = SQLBuilder.select();

            assertThrows(IllegalArgumentException.class, () -> builder.addColumn(null));
        }

        @Test
        void addEmptyColumnThrowsException() {
            SQLBuilder builder = SQLBuilder.select();

            assertThrows(IllegalArgumentException.class, () -> builder.addColumn(""));
        }
    }

    @Nested
    class Merge {

        @Test
        void mergeNullThrowsException() {
            SQLBuilder builder = SQLBuilder.select("id");

            assertThrows(IllegalArgumentException.class, () -> builder.merge(null));
        }

        @Test
        void mergeColumns() {
            SQLBuilder builder1 = SQLBuilder.select("id", "name");
            SQLBuilder builder2 = SQLBuilder.select("status", "count");

            builder1.merge(builder2);

            List<String> columns = builder1.getColumns();
            assertEquals(4, columns.size());
        }

        @Test
        void mergeAvoidsDuplicateColumns() {
            SQLBuilder builder1 = SQLBuilder.select("id", "name");
            SQLBuilder builder2 = SQLBuilder.select("id", "status");

            builder1.merge(builder2);

            List<String> columns = builder1.getColumns();
            assertEquals(3, columns.size()); // id, name, status (no duplicate id)
        }

        @Test
        void mergeWhereConditions() {
            SQLBuilder builder1 = SQLBuilder.select("*")
                    .from("events")
                    .where("status", "=", l("active"));

            SQLBuilder builder2 = SQLBuilder.select()
                    .where("count", ">", l(10));

            builder1.merge(builder2);
            String sql = builder1.build(PROFILE_ID);

            assertTrue(sql.contains("WHERE status = 'active'"));
            assertTrue(sql.contains("count > 10"));
        }

        @Test
        void mergeGroupBy() {
            SQLBuilder builder1 = SQLBuilder.select("status", "COUNT(*)")
                    .from("events")
                    .groupBy("status");

            SQLBuilder builder2 = SQLBuilder.select()
                    .groupBy("type");

            builder1.merge(builder2);
            String sql = builder1.build(PROFILE_ID);

            assertTrue(sql.contains("GROUP BY status, type"));
        }

        @Test
        void mergeOrderBy() {
            SQLBuilder builder1 = SQLBuilder.select("*")
                    .from("events")
                    .orderBy("created_at");

            SQLBuilder builder2 = SQLBuilder.select()
                    .orderBy("id", "DESC");

            builder1.merge(builder2);
            String sql = builder1.build(PROFILE_ID);

            assertTrue(sql.contains("ORDER BY created_at, id DESC"));
        }
    }

    @Nested
    class CompositeConditions {

        @Test
        void andComposite() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(and(
                            eq("status", l("active")),
                            gt("count", l(10))
                    ))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("(status = 'active' AND count > 10)"));
        }

        @Test
        void orComposite() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(or(
                            eq("status", l("active")),
                            eq("status", l("pending"))
                    ))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("(status = 'active' OR status = 'pending')"));
        }

        @Test
        void nestedComposite() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(or(
                            and(eq("status", l("active")), gt("count", l(10))),
                            eq("priority", l("high"))
                    ))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("((status = 'active' AND count > 10) OR priority = 'high')"));
        }
    }

    @Nested
    class SpecialConditions {

        @Test
        void rawCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(raw("custom_function(col) > 5"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WHERE custom_function(col) > 5"));
        }

        @Test
        void notInOrNullCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .where(notInOrNull("status", "deleted", "archived"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("NOT IN ('deleted', 'archived')") || sql.contains("IS NULL"));
        }

        @Test
        void existsCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .where(exists("SELECT 1 FROM threads t WHERE t.event_id = e.id"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("EXISTS (SELECT 1 FROM threads t WHERE t.event_id = e.id)"));
        }

        @Test
        void notExistsCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .where(notExists("SELECT 1 FROM deleted d WHERE d.event_id = e.id"))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM deleted d WHERE d.event_id = e.id)"));
        }
    }

    @Nested
    class CteAndProfileId {

        @Test
        void includesFirstSampleCte() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("WITH first_sample AS"));
            assertTrue(sql.contains("profile_id = 'test-profile'"));
        }

        @Test
        void crossJoinsFirstSample() {
            String sql = SQLBuilder.select("*")
                    .from("events")
                    .build(PROFILE_ID);

            assertTrue(sql.contains("CROSS JOIN first_sample fs"));
        }
    }

    @Nested
    class ValueTypeLiteral {

        @Test
        void stringLiteral() {
            ValueType value = l("test");
            assertEquals("'test'", value.format());
        }

        @Test
        void integerLiteral() {
            ValueType value = l(42);
            assertEquals("42", value.format());
        }

        @Test
        void longLiteral() {
            ValueType value = l(123456789L);
            assertEquals("123456789", value.format());
        }

        @Test
        void booleanLiteral() {
            ValueType trueVal = l(true);
            ValueType falseVal = l(false);

            assertEquals("true", trueVal.format());
            assertEquals("false", falseVal.format());
        }

        @Test
        void unsupportedTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> l(3.14));
        }
    }

    @Nested
    class ColumnReference {

        @Test
        void columnReference() {
            Column col = c("table.column");
            assertEquals("table.column", col.format());
        }

        @Test
        void columnInCondition() {
            String sql = SQLBuilder.select("*")
                    .from("events", "e")
                    .join("threads t", eq("e.thread_id", c("t.id")))
                    .build(PROFILE_ID);

            assertTrue(sql.contains("e.thread_id = t.id"));
        }
    }
}
