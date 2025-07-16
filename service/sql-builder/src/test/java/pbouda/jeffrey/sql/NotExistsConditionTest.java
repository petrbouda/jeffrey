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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;
import static pbouda.jeffrey.sql.SQLBuilder.l;

class NotExistsConditionTest {

    @Nested
    @DisplayName("String Subquery Tests")
    class StringSubqueryTests {

        @Test
        @DisplayName("Should create NOT EXISTS condition with simple string subquery")
        void shouldCreateNotExistsConditionWithSimpleStringSubquery() {
            NotExistsCondition condition = new NotExistsCondition("SELECT 1 FROM orders WHERE user_id = 123");
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should create NOT EXISTS condition with complex string subquery")
        void shouldCreateNotExistsConditionWithComplexStringSubquery() {
            String subquery = "SELECT o.id FROM orders o INNER JOIN products p ON o.product_id = p.id WHERE p.category = 'electronics' AND o.amount > 100";
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (" + subquery + ")", result);
        }

        @Test
        @DisplayName("Should handle empty string subquery")
        void shouldHandleEmptyStringSubquery() {
            NotExistsCondition condition = new NotExistsCondition("");
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS ()", result);
        }

        @Test
        @DisplayName("Should handle subquery with whitespace")
        void shouldHandleSubqueryWithWhitespace() {
            NotExistsCondition condition = new NotExistsCondition("  SELECT 1 FROM orders  ");
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (  SELECT 1 FROM orders  )", result);
        }
    }

    @Nested
    @DisplayName("SQLBuilder Subquery Tests")
    class SQLBuilderSubqueryTests {

        @Test
        @DisplayName("Should create NOT EXISTS condition with SQLBuilder subquery")
        void shouldCreateNotExistsConditionWithSQLBuilderSubquery() {
            SQLBuilder subquery = SQLBuilder.select("1")
                    .from("orders")
                    .where("user_id", "=", l(123));
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should create NOT EXISTS condition with complex SQLBuilder subquery")
        void shouldCreateNotExistsConditionWithComplexSQLBuilderSubquery() {
            SQLBuilder subquery = SQLBuilder.select("o.id")
                    .from("orders o")
                    .join("products p", "o.product_id = p.id")
                    .where("p.category", "=", l("electronics"))
                    .and("o.amount", ">", l(100));
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            String expected = "NOT EXISTS (SELECT o.id FROM orders o INNER JOIN products p ON o.product_id = p.id WHERE p.category = 'electronics' AND o.amount > 100)";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle SQLBuilder with all clauses")
        void shouldHandleSQLBuilderWithAllClauses() {
            SQLBuilder subquery = SQLBuilder.select("COUNT(*)")
                    .from("orders")
                    .where("status", "=", l("completed"))
                    .groupBy("user_id")
                    .having("COUNT(*)", ">", l(5))
                    .orderBy("COUNT(*)", "DESC");
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            String expected = "NOT EXISTS (SELECT COUNT(*) FROM orders WHERE status = 'completed' GROUP BY user_id HAVING COUNT(*) > 5 ORDER BY COUNT(*) DESC)";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle empty SQLBuilder subquery")
        void shouldHandleEmptySQLBuilderSubquery() {
            SQLBuilder subquery = new SQLBuilder();
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT *)", result);
        }

        @Test
        @DisplayName("Should handle SQLBuilder with only FROM clause")
        void shouldHandleSQLBuilderWithOnlyFromClause() {
            SQLBuilder subquery = SQLBuilder.select("*").from("orders");
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT * FROM orders)", result);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null string subquery")
        void shouldHandleNullStringSubquery() {
            NotExistsCondition condition = new NotExistsCondition((String) null);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (null)", result);
        }

        @Test
        @DisplayName("Should handle null SQLBuilder subquery")
        void shouldHandleNullSQLBuilderSubquery() {
            assertThrows(NullPointerException.class, () -> {
                new NotExistsCondition((SQLBuilder) null);
            });
        }

        @Test
        @DisplayName("Should handle subquery with special characters")
        void shouldHandleSubqueryWithSpecialCharacters() {
            String subquery = "SELECT 1 FROM orders WHERE description LIKE '%special-chars!@#$%'";
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (" + subquery + ")", result);
        }

        @Test
        @DisplayName("Should handle very long subquery")
        void shouldHandleVeryLongSubquery() {
            StringBuilder longSubquery = new StringBuilder("SELECT ");
            for (int i = 0; i < 50; i++) {
                longSubquery.append("column").append(i);
                if (i < 49) longSubquery.append(", ");
            }
            longSubquery.append(" FROM very_long_table_name");
            
            NotExistsCondition condition = new NotExistsCondition(longSubquery.toString());
            
            String result = condition.toSql();
            
            assertTrue(result.startsWith("NOT EXISTS (SELECT column0, column1"));
            assertTrue(result.endsWith("FROM very_long_table_name)"));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should work with nested NOT EXISTS conditions")
        void shouldWorkWithNestedNotExistsConditions() {
            SQLBuilder innerSubquery = SQLBuilder.select("1")
                    .from("order_items")
                    .where("order_id", "=", SQLBuilder.c("orders.id"));
            
            SQLBuilder outerSubquery = SQLBuilder.select("1")
                    .from("orders")
                    .where("user_id", "=", l(123))
                    .and(new NotExistsCondition(innerSubquery));
            
            NotExistsCondition condition = new NotExistsCondition(outerSubquery);
            
            String result = condition.toSql();
            
            String expected = "NOT EXISTS (SELECT 1 FROM orders WHERE user_id = 123 AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_id = orders.id))";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should work with correlated subqueries")
        void shouldWorkWithCorrelatedSubqueries() {
            SQLBuilder subquery = SQLBuilder.select("1")
                    .from("orders o")
                    .where("o.user_id", "=", SQLBuilder.c("users.id"))
                    .and("o.status", "=", l("completed"));
            
            NotExistsCondition condition = new NotExistsCondition(subquery);
            
            String result = condition.toSql();
            
            String expected = "NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = users.id AND o.status = 'completed')";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should work with mixed EXISTS and NOT EXISTS")
        void shouldWorkWithMixedExistsAndNotExists() {
            SQLBuilder existsSubquery = SQLBuilder.select("1")
                    .from("orders")
                    .where("user_id", "=", SQLBuilder.c("users.id"));
            
            SQLBuilder notExistsSubquery = SQLBuilder.select("1")
                    .from("complaints")
                    .where("user_id", "=", SQLBuilder.c("users.id"));
            
            SQLBuilder query = SQLBuilder.select("*")
                    .from("users")
                    .where(SQLBuilder.exists(existsSubquery))
                    .and(new NotExistsCondition(notExistsSubquery));
            
            String result = query.build();
            
            String expected = "SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders WHERE user_id = users.id) AND NOT EXISTS (SELECT 1 FROM complaints WHERE user_id = users.id)";
            assertEquals(expected, result);
        }
    }
}