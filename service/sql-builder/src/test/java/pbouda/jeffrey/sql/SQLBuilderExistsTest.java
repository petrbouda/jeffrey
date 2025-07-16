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
import static pbouda.jeffrey.sql.SQLBuilder.*;

class SQLBuilderExistsTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create EXISTS condition with string subquery")
        void shouldCreateExistsConditionWithStringSubquery() {
            Condition condition = exists("SELECT 1 FROM orders WHERE user_id = 123");
            
            String result = condition.toSql();
            
            assertEquals("EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should create EXISTS condition with SQLBuilder subquery")
        void shouldCreateExistsConditionWithSQLBuilderSubquery() {
            SQLBuilder subquery = select("1").from("orders").where("user_id", "=", l(123));
            Condition condition = exists(subquery);
            
            String result = condition.toSql();
            
            assertEquals("EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should return ExistsCondition instance")
        void shouldReturnExistsConditionInstance() {
            Condition condition = exists("SELECT 1 FROM orders");
            
            assertInstanceOf(ExistsCondition.class, condition);
        }
    }

    @Nested
    @DisplayName("WHERE Clause Integration Tests")
    class WhereClauseIntegrationTests {

        @Test
        @DisplayName("Should use EXISTS in WHERE clause with string subquery")
        void shouldUseExistsInWhereClauseWithStringSubquery() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where(exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id)", result);
        }

        @Test
        @DisplayName("Should use EXISTS in WHERE clause with SQLBuilder subquery")
        void shouldUseExistsInWhereClauseWithSQLBuilderSubquery() {
            SQLBuilder subquery = select("1")
                    .from("orders")
                    .where("user_id", "=", c("users.id"));
            
            SQLBuilder query = select("*")
                    .from("users")
                    .where(exists(subquery));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders WHERE user_id = users.id)", result);
        }

        @Test
        @DisplayName("Should combine EXISTS with other conditions using AND")
        void shouldCombineExistsWithOtherConditionsUsingAnd() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where("active", "=", l(true))
                    .and(exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE active = true AND EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id)", result);
        }

        @Test
        @DisplayName("Should combine EXISTS with other conditions using OR")
        void shouldCombineExistsWithOtherConditionsUsingOr() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where("vip", "=", l(true))
                    .or(exists("SELECT 1 FROM orders WHERE orders.user_id = users.id AND orders.amount > 1000"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE vip = true OR EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id AND orders.amount > 1000)", result);
        }

        @Test
        @DisplayName("Should handle multiple EXISTS conditions")
        void shouldHandleMultipleExistsConditions() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where(exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"))
                    .and(exists("SELECT 1 FROM reviews WHERE reviews.user_id = users.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id) AND EXISTS (SELECT 1 FROM reviews WHERE reviews.user_id = users.id)", result);
        }
    }

    @Nested
    @DisplayName("Complex Query Integration Tests")
    class ComplexQueryIntegrationTests {

        @Test
        @DisplayName("Should work with joins and EXISTS")
        void shouldWorkWithJoinsAndExists() {
            SQLBuilder query = select("u.name", "p.title")
                    .from("users u")
                    .join("profiles p", "u.id = p.user_id")
                    .where("u.active", "=", l(true))
                    .and(exists("SELECT 1 FROM orders WHERE orders.user_id = u.id"));
            
            String result = query.build();
            
            String expected = "SELECT u.name, p.title FROM users u INNER JOIN profiles p ON u.id = p.user_id WHERE u.active = true AND EXISTS (SELECT 1 FROM orders WHERE orders.user_id = u.id)";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should work with GROUP BY and EXISTS")
        void shouldWorkWithGroupByAndExists() {
            SQLBuilder query = select("department", "COUNT(*)")
                    .from("employees")
                    .where(exists("SELECT 1 FROM projects WHERE projects.manager_id = employees.id"))
                    .groupBy("department");
            
            String result = query.build();
            
            assertEquals("SELECT department, COUNT(*) FROM employees WHERE EXISTS (SELECT 1 FROM projects WHERE projects.manager_id = employees.id) GROUP BY department", result);
        }

        @Test
        @DisplayName("Should work with ORDER BY and EXISTS")
        void shouldWorkWithOrderByAndExists() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where(exists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"))
                    .orderBy("name");
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) ORDER BY name", result);
        }

        @Test
        @DisplayName("Should work with nested EXISTS conditions")
        void shouldWorkWithNestedExistsConditions() {
            SQLBuilder innerSubquery = select("1")
                    .from("order_items oi")
                    .where("oi.order_id", "=", c("o.id"))
                    .and("oi.product_id", "=", l(123));
            
            SQLBuilder outerSubquery = select("1")
                    .from("orders o")
                    .where("o.user_id", "=", c("users.id"))
                    .and(exists(innerSubquery));
            
            SQLBuilder query = select("*")
                    .from("users")
                    .where(exists(outerSubquery));
            
            String result = query.build();
            
            String expected = "SELECT * FROM users WHERE EXISTS (SELECT 1 FROM orders o WHERE o.user_id = users.id AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = 123))";
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Composite Condition Tests")
    class CompositeConditionTests {

        @Test
        @DisplayName("Should work with AND composite conditions")
        void shouldWorkWithAndCompositeConditions() {
            Condition condition = and(
                    eq("active", l(true)),
                    exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"),
                    gt("created_at", l("2024-01-01"))
            );
            
            SQLBuilder query = select("*").from("users").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE (active = true AND EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id) AND created_at > '2024-01-01')", result);
        }

        @Test
        @DisplayName("Should work with OR composite conditions")
        void shouldWorkWithOrCompositeConditions() {
            Condition condition = or(
                    eq("vip", l(true)),
                    exists("SELECT 1 FROM orders WHERE orders.user_id = users.id AND orders.amount > 1000")
            );
            
            SQLBuilder query = select("*").from("users").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE (vip = true OR EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id AND orders.amount > 1000))", result);
        }

        @Test
        @DisplayName("Should work with mixed composite conditions")
        void shouldWorkWithMixedCompositeConditions() {
            Condition condition = and(
                    eq("active", l(true)),
                    or(
                            exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"),
                            exists("SELECT 1 FROM reviews WHERE reviews.user_id = users.id")
                    )
            );
            
            SQLBuilder query = select("*").from("users").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE (active = true AND (EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id) OR EXISTS (SELECT 1 FROM reviews WHERE reviews.user_id = users.id)))", result);
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Should find users with recent orders")
        void shouldFindUsersWithRecentOrders() {
            SQLBuilder recentOrdersSubquery = select("1")
                    .from("orders")
                    .where("user_id", "=", c("users.id"))
                    .and("created_at", ">=", l("2024-01-01"));
            
            SQLBuilder query = select("id", "name", "email")
                    .from("users")
                    .where("active", "=", l(true))
                    .and(exists(recentOrdersSubquery))
                    .orderBy("name");
            
            String result = query.build();
            
            String expected = "SELECT id, name, email FROM users WHERE active = true AND EXISTS (SELECT 1 FROM orders WHERE user_id = users.id AND created_at >= '2024-01-01') ORDER BY name";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find products never ordered")
        void shouldFindProductsNeverOrdered() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where("active", "=", l(true))
                    .and(exists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE active = true AND EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id)", result);
        }

        @Test
        @DisplayName("Should find customers with high-value orders")
        void shouldFindCustomersWithHighValueOrders() {
            SQLBuilder highValueOrdersSubquery = select("1")
                    .from("orders")
                    .where("user_id", "=", c("users.id"))
                    .and("total_amount", ">", l(1000))
                    .and("status", "=", l("completed"));
            
            SQLBuilder query = select("users.id", "users.name", "COUNT(orders.id) as order_count")
                    .from("users")
                    .leftJoin("orders", "users.id = orders.user_id")
                    .where(exists(highValueOrdersSubquery))
                    .groupBy("users.id", "users.name")
                    .orderBy("order_count", "DESC");
            
            String result = query.build();
            
            String expected = "SELECT users.id, users.name, COUNT(orders.id) as order_count FROM users LEFT JOIN orders ON users.id = orders.user_id WHERE EXISTS (SELECT 1 FROM orders WHERE user_id = users.id AND total_amount > 1000 AND status = 'completed') GROUP BY users.id, users.name ORDER BY order_count DESC";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find categories with available products")
        void shouldFindCategoriesWithAvailableProducts() {
            SQLBuilder availableProductsSubquery = select("1")
                    .from("products p")
                    .where("p.category_id", "=", c("categories.id"))
                    .and("p.in_stock", "=", l(true))
                    .and("p.active", "=", l(true));
            
            SQLBuilder query = select("categories.id", "categories.name")
                    .from("categories")
                    .where("categories.active", "=", l(true))
                    .and(exists(availableProductsSubquery))
                    .orderBy("categories.name");
            
            String result = query.build();
            
            String expected = "SELECT categories.id, categories.name FROM categories WHERE categories.active = true AND EXISTS (SELECT 1 FROM products p WHERE p.category_id = categories.id AND p.in_stock = true AND p.active = true) ORDER BY categories.name";
            assertEquals(expected, result);
        }
    }
}