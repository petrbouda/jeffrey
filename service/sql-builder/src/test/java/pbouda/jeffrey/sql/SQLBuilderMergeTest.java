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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static pbouda.jeffrey.sql.SQLBuilder.l;

class SQLBuilderMergeTest {

    @Nested
    @DisplayName("Basic Merge Tests")
    class BasicMergeTests {

        @Test
        @DisplayName("Should merge select columns from both criteria")
        void shouldMergeSelectColumns() {
            SQLBuilder criteria1 = SQLBuilder.select("col1", "col2");
            SQLBuilder criteria2 = SQLBuilder.select("col3", "col4");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertTrue(result.contains("col1, col2, col3, col4"));
        }

        @Test
        @DisplayName("Should merge from tables from both criteria")
        void shouldMergeFromTables() {
            SQLBuilder criteria1 = SQLBuilder.select("*").from("table1");
            SQLBuilder criteria2 = SQLBuilder.select("*").from("table2");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertTrue(result.contains("FROM table1, table2"));
        }

        @Test
        @DisplayName("Should merge joins from both criteria")
        void shouldMergeJoins() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .join("orders", "users.id = orders.user_id");

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .leftJoin("payments", "orders.id = payments.order_id");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertAll(
                    () -> assertTrue(result.contains("INNER JOIN orders ON users.id = orders.user_id")),
                    () -> assertTrue(result.contains("LEFT JOIN payments ON orders.id = payments.order_id"))
            );
        }

        @Test
        @DisplayName("Should merge where conditions from both criteria")
        void shouldMergeWhereConditions() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .where("age", ">", l(18));

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .where("status", "=", l("active"));

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertAll(
                    () -> assertTrue(result.contains("age > 18")),
                    () -> assertTrue(result.contains("status = 'active'"))
            );
        }

        @Test
        @DisplayName("Should merge group by columns from both criteria")
        void shouldMergeGroupByColumns() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .groupBy("department");

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .groupBy("role", "location");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertTrue(result.contains("GROUP BY department, role, location"));
        }

        @Test
        @DisplayName("Should merge having conditions from both criteria")
        void shouldMergeHavingConditions() {
            SQLBuilder criteria1 = SQLBuilder.select("department", "COUNT(*) as count")
                    .from("users")
                    .groupBy("department")
                    .having("COUNT(*)", ">", l(5));

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .having("AVG(salary)", "<", l(50000));

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertAll(
                    () -> assertTrue(result.contains("COUNT(*) > 5")),
                    () -> assertTrue(result.contains("AVG(salary) < 50000"))
            );
        }

        @Test
        @DisplayName("Should merge order by columns from both criteria")
        void shouldMergeOrderByColumns() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .orderBy("name");

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .orderBy("age", "DESC");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertTrue(result.contains("ORDER BY name, age DESC"));
        }
    }

    @Nested
    @DisplayName("Complex Merge Tests")
    class ComplexMergeTests {

        @Test
        @DisplayName("Should merge complete complex criteria")
        void shouldMergeComplexCriteria() {
            SQLBuilder criteria1 = SQLBuilder.select("u.name", "u.email")
                    .from("users u")
                    .join("orders o", "u.id = o.user_id")
                    .where("u.age", ">", l(18))
                    .and("u.status", "=", l("active"))
                    .groupBy("u.id")
                    .having("COUNT(o.id)", ">", l(1))
                    .orderBy("u.name");

            SQLBuilder criteria2 = SQLBuilder.select("p.amount", "p.status")
                    .from("payments p")
                    .leftJoin("discounts d", "p.discount_id = d.id")
                    .where("p.amount", ">", l(100))
                    .or("p.status", "=", l("pending"))
                    .groupBy("p.status")
                    .having("SUM(p.amount)", "<", l(1000))
                    .orderBy("p.amount", "DESC");

            criteria1.merge(criteria2);

            String result = criteria1.build();

            assertAll(
                    () -> assertTrue(result.contains("u.name, u.email, p.amount, p.status")),
                    () -> assertTrue(result.contains("FROM users u, payments p")),
                    () -> assertTrue(result.contains("INNER JOIN orders o ON u.id = o.user_id")),
                    () -> assertTrue(result.contains("LEFT JOIN discounts d ON p.discount_id = d.id")),
                    () -> assertTrue(result.contains("u.age > 18")),
                    () -> assertTrue(result.contains("u.status = 'active'")),
                    () -> assertTrue(result.contains("p.amount > 100")),
                    () -> assertTrue(result.contains("p.status = 'pending'")),
                    () -> assertTrue(result.contains("GROUP BY u.id, p.status")),
                    () -> assertTrue(result.contains("COUNT(o.id) > 1")),
                    () -> assertTrue(result.contains("SUM(p.amount) < 1000")),
                    () -> assertTrue(result.contains("ORDER BY u.name, p.amount DESC"))
            );
        }

        @Test
        @DisplayName("Should merge with different condition types")
        void shouldMergeWithDifferentConditionTypes() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .where("age", ">=", l(21))
                    .and(SQLBuilder.in("role", "admin", "manager"));

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .where(SQLBuilder.like("name", "%John%"))
                    .or(SQLBuilder.and(
                            SQLBuilder.eq("department", l("IT")),
                            SQLBuilder.gt("salary", l(50000))
                    ));

            criteria1.merge(criteria2);

            String expected = "SELECT * FROM users WHERE age >= 21 AND role IN ('admin', 'manager') AND (name LIKE '%John%' OR (department = 'IT' AND salary > 50000))";
            assertEquals(expected, criteria1.build());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesAndValidation {

        @Test
        @DisplayName("Should throw exception when merging null criteria")
        void shouldThrowExceptionWhenMergingNull() {
            SQLBuilder criteria = SQLBuilder.select("*").from("users");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.merge(null)
            );

            assertEquals("Cannot merge null SqlCriteria", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle merging empty criteria")
        void shouldHandleMergingEmptyCriteria() {
            SQLBuilder criteria1 = SQLBuilder.select("*").from("users").where("id", "=", l(1));
            SQLBuilder criteria2 = new SQLBuilder();

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertEquals("SELECT * FROM users WHERE id = 1", result);
        }

        @Test
        @DisplayName("Should handle merging into empty criteria")
        void shouldHandleMergingIntoEmptyCriteria() {
            SQLBuilder criteria1 = new SQLBuilder();
            SQLBuilder criteria2 = SQLBuilder.select("name").from("users").where("active", "=", l(true));

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertEquals("SELECT name FROM users WHERE active = true", result);
        }

        @Test
        @DisplayName("Should handle merging criteria with same elements")
        void shouldHandleMergingCriteriaWithSameElements() {
            SQLBuilder criteria1 = SQLBuilder.select("name")
                    .from("users")
                    .where("active", "=", l(true))
                    .orderBy("name");

            SQLBuilder criteria2 = SQLBuilder.select("name")
                    .from("users")
                    .where("active", "=", l(true))
                    .orderBy("name");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            // Should contain duplicates as merge doesn't deduplicate
            assertAll(
                    () -> assertTrue(result.contains("name, name")),
                    () -> assertTrue(result.contains("FROM users, users")),
                    () -> assertTrue(result.contains("ORDER BY name, name"))
            );
        }

        @Test
        @DisplayName("Should return same instance for method chaining")
        void shouldReturnSameInstanceForMethodChaining() {
            SQLBuilder criteria1 = SQLBuilder.select("*").from("users");
            SQLBuilder criteria2 = SQLBuilder.select("*").from("orders");

            SQLBuilder result = criteria1.merge(criteria2);

            assertSame(criteria1, result);
        }

        @Test
        @DisplayName("Should allow chaining after merge")
        void shouldAllowChainingAfterMerge() {
            SQLBuilder criteria1 = SQLBuilder.select("name").from("users");
            SQLBuilder criteria2 = SQLBuilder.select("amount").from("orders");

            String result = criteria1
                    .merge(criteria2)
                    .where("active", "=", l(true))
                    .orderBy("name")
                    .build();

            assertAll(
                    () -> assertTrue(result.contains("name, amount")),
                    () -> assertTrue(result.contains("FROM users, orders")),
                    () -> assertTrue(result.contains("WHERE active = true")),
                    () -> assertTrue(result.contains("ORDER BY name"))
            );
        }
    }

    @Nested
    @DisplayName("Specific Component Merge Tests")
    class SpecificComponentMergeTests {

        @Test
        @DisplayName("Should preserve order when merging select columns")
        void shouldPreserveOrderWhenMergingSelectColumns() {
            SQLBuilder criteria1 = SQLBuilder.select("a", "b");
            SQLBuilder criteria2 = SQLBuilder.select("c", "d");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertTrue(result.contains("SELECT a, b, c, d"));
        }

        @Test
        @DisplayName("Should preserve order when merging joins")
        void shouldPreserveOrderWhenMergingJoins() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .join("orders", "users.id = orders.user_id");

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .rightJoin("products", "orders.product_id = products.id")
                    .leftJoin("categories", "products.category_id = categories.id");

            criteria1.merge(criteria2);

            String result = criteria1.build();

            int innerJoinPos = result.indexOf("INNER JOIN orders");
            int rightJoinPos = result.indexOf("RIGHT JOIN products");
            int leftJoinPos = result.indexOf("LEFT JOIN categories");

            assertAll(
                    () -> assertTrue(innerJoinPos > 0),
                    () -> assertTrue(rightJoinPos > innerJoinPos),
                    () -> assertTrue(leftJoinPos > rightJoinPos)
            );
        }

        @Test
        @DisplayName("Should handle merging with table aliases")
        void shouldHandleMergingWithTableAliases() {
            SQLBuilder criteria1 = SQLBuilder.select("u.name")
                    .from("users", "u");

            SQLBuilder criteria2 = SQLBuilder.select("o.amount")
                    .from("orders", "o");

            criteria1.merge(criteria2);

            String result = criteria1.build();
            assertAll(
                    () -> assertTrue(result.contains("u.name, o.amount")),
                    () -> assertTrue(result.contains("FROM users u, orders o"))
            );
        }

        @Test
        @DisplayName("Should merge complex where conditions correctly")
        void shouldMergeComplexWhereConditionsCorrectly() {
            SQLBuilder criteria1 = SQLBuilder.select("*")
                    .from("users")
                    .where("age", ">", l(18))
                    .and("status", "=", l("active"));

            SQLBuilder criteria2 = SQLBuilder.select("*")
                    .where("department", "!=", l("HR"))
                    .or(SQLBuilder.and(
                            SQLBuilder.eq("role", l("admin")),
                            SQLBuilder.gte("salary", l(100000))
                    ));

            criteria1.merge(criteria2);

            String result = criteria1.build();

            // The merged query should contain all conditions in the order they were added
            assertAll(
                    () -> assertTrue(result.contains("age > 18")),
                    () -> assertTrue(result.contains("status = 'active'")),
                    () -> assertTrue(result.contains("department != 'HR'")),
                    () -> assertTrue(result.contains("role = 'admin'")),
                    () -> assertTrue(result.contains("salary >= 100000"))
            );
        }
    }
}
