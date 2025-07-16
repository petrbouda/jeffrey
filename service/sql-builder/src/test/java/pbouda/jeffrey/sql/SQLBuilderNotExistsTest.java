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

class SQLBuilderNotExistsTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create NOT EXISTS condition with string subquery")
        void shouldCreateNotExistsConditionWithStringSubquery() {
            Condition condition = notExists("SELECT 1 FROM orders WHERE user_id = 123");
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should create NOT EXISTS condition with SQLBuilder subquery")
        void shouldCreateNotExistsConditionWithSQLBuilderSubquery() {
            SQLBuilder subquery = select("1").from("orders").where("user_id", "=", l(123));
            Condition condition = notExists(subquery);
            
            String result = condition.toSql();
            
            assertEquals("NOT EXISTS (SELECT 1 FROM orders WHERE user_id = 123)", result);
        }

        @Test
        @DisplayName("Should return NotExistsCondition instance")
        void shouldReturnNotExistsConditionInstance() {
            Condition condition = notExists("SELECT 1 FROM orders");
            
            assertInstanceOf(NotExistsCondition.class, condition);
        }
    }

    @Nested
    @DisplayName("WHERE Clause Integration Tests")
    class WhereClauseIntegrationTests {

        @Test
        @DisplayName("Should use NOT EXISTS in WHERE clause with string subquery")
        void shouldUseNotExistsInWhereClauseWithStringSubquery() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id)", result);
        }

        @Test
        @DisplayName("Should use NOT EXISTS in WHERE clause with SQLBuilder subquery")
        void shouldUseNotExistsInWhereClauseWithSQLBuilderSubquery() {
            SQLBuilder subquery = select("1")
                    .from("order_items")
                    .where("product_id", "=", c("products.id"));
            
            SQLBuilder query = select("*")
                    .from("products")
                    .where(notExists(subquery));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_id = products.id)", result);
        }

        @Test
        @DisplayName("Should combine NOT EXISTS with other conditions using AND")
        void shouldCombineNotExistsWithOtherConditionsUsingAnd() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where("active", "=", l(true))
                    .and(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE active = true AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id)", result);
        }

        @Test
        @DisplayName("Should combine NOT EXISTS with other conditions using OR")
        void shouldCombineNotExistsWithOtherConditionsUsingOr() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where("discontinued", "=", l(true))
                    .or(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id AND order_items.quantity > 0"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE discontinued = true OR NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id AND order_items.quantity > 0)", result);
        }

        @Test
        @DisplayName("Should handle multiple NOT EXISTS conditions")
        void shouldHandleMultipleNotExistsConditions() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where(notExists("SELECT 1 FROM orders WHERE orders.user_id = users.id"))
                    .and(notExists("SELECT 1 FROM reviews WHERE reviews.user_id = users.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE NOT EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id) AND NOT EXISTS (SELECT 1 FROM reviews WHERE reviews.user_id = users.id)", result);
        }

        @Test
        @DisplayName("Should mix EXISTS and NOT EXISTS conditions")
        void shouldMixExistsAndNotExistsConditions() {
            SQLBuilder query = select("*")
                    .from("users")
                    .where(exists("SELECT 1 FROM profiles WHERE profiles.user_id = users.id"))
                    .and(notExists("SELECT 1 FROM complaints WHERE complaints.user_id = users.id"));
            
            String result = query.build();
            
            assertEquals("SELECT * FROM users WHERE EXISTS (SELECT 1 FROM profiles WHERE profiles.user_id = users.id) AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaints.user_id = users.id)", result);
        }
    }

    @Nested
    @DisplayName("Complex Query Integration Tests")
    class ComplexQueryIntegrationTests {

        @Test
        @DisplayName("Should work with joins and NOT EXISTS")
        void shouldWorkWithJoinsAndNotExists() {
            SQLBuilder query = select("p.id", "p.name", "c.name")
                    .from("products p")
                    .join("categories c", "p.category_id = c.id")
                    .where("p.active", "=", l(true))
                    .and(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = p.id"));
            
            String result = query.build();
            
            String expected = "SELECT p.id, p.name, c.name FROM products p INNER JOIN categories c ON p.category_id = c.id WHERE p.active = true AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = p.id)";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should work with GROUP BY and NOT EXISTS")
        void shouldWorkWithGroupByAndNotExists() {
            SQLBuilder query = select("category_id", "COUNT(*)")
                    .from("products")
                    .where(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"))
                    .groupBy("category_id");
            
            String result = query.build();
            
            assertEquals("SELECT category_id, COUNT(*) FROM products WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) GROUP BY category_id", result);
        }

        @Test
        @DisplayName("Should work with ORDER BY and NOT EXISTS")
        void shouldWorkWithOrderByAndNotExists() {
            SQLBuilder query = select("*")
                    .from("products")
                    .where(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"))
                    .orderBy("name");
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) ORDER BY name", result);
        }

        @Test
        @DisplayName("Should work with nested NOT EXISTS conditions")
        void shouldWorkWithNestedNotExistsConditions() {
            SQLBuilder innerSubquery = select("1")
                    .from("order_items oi")
                    .where("oi.order_id", "=", c("o.id"))
                    .and("oi.product_id", "=", l(123));
            
            SQLBuilder outerSubquery = select("1")
                    .from("orders o")
                    .where("o.user_id", "=", c("users.id"))
                    .and(notExists(innerSubquery));
            
            SQLBuilder query = select("*")
                    .from("users")
                    .where(notExists(outerSubquery));
            
            String result = query.build();
            
            String expected = "SELECT * FROM users WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = users.id AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = 123))";
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
                    notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"),
                    gt("created_at", l("2024-01-01"))
            );
            
            SQLBuilder query = select("*").from("products").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE (active = true AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) AND created_at > '2024-01-01')", result);
        }

        @Test
        @DisplayName("Should work with OR composite conditions")
        void shouldWorkWithOrCompositeConditions() {
            Condition condition = or(
                    eq("discontinued", l(true)),
                    notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id AND order_items.quantity > 0")
            );
            
            SQLBuilder query = select("*").from("products").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE (discontinued = true OR NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id AND order_items.quantity > 0))", result);
        }

        @Test
        @DisplayName("Should work with mixed composite conditions")
        void shouldWorkWithMixedCompositeConditions() {
            Condition condition = and(
                    eq("active", l(true)),
                    or(
                            notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"),
                            lt("stock_quantity", l(5))
                    )
            );
            
            SQLBuilder query = select("*").from("products").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE (active = true AND (NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) OR stock_quantity < 5))", result);
        }

        @Test
        @DisplayName("Should work with mixed EXISTS and NOT EXISTS in composite conditions")
        void shouldWorkWithMixedExistsAndNotExistsInCompositeConditions() {
            Condition condition = and(
                    exists("SELECT 1 FROM categories WHERE categories.id = products.category_id"),
                    notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"),
                    eq("active", l(true))
            );
            
            SQLBuilder query = select("*").from("products").where(condition);
            
            String result = query.build();
            
            assertEquals("SELECT * FROM products WHERE (EXISTS (SELECT 1 FROM categories WHERE categories.id = products.category_id) AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) AND active = true)", result);
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Should find products that have never been ordered")
        void shouldFindProductsThatHaveNeverBeenOrdered() {
            SQLBuilder query = select("id", "name", "price")
                    .from("products")
                    .where("active", "=", l(true))
                    .and(notExists("SELECT 1 FROM order_items WHERE order_items.product_id = products.id"))
                    .orderBy("name");
            
            String result = query.build();
            
            String expected = "SELECT id, name, price FROM products WHERE active = true AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_items.product_id = products.id) ORDER BY name";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find users with no orders")
        void shouldFindUsersWithNoOrders() {
            SQLBuilder orderSubquery = select("1")
                    .from("orders")
                    .where("user_id", "=", c("users.id"));
            
            SQLBuilder query = select("*")
                    .from("users")
                    .where("active", "=", l(true))
                    .and(notExists(orderSubquery))
                    .orderBy("created_at", "DESC");
            
            String result = query.build();
            
            String expected = "SELECT * FROM users WHERE active = true AND NOT EXISTS (SELECT 1 FROM orders WHERE user_id = users.id) ORDER BY created_at DESC";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find categories with no active products")
        void shouldFindCategoriesWithNoActiveProducts() {
            SQLBuilder activeProductsSubquery = select("1")
                    .from("products p")
                    .where("p.category_id", "=", c("categories.id"))
                    .and("p.active", "=", l(true));
            
            SQLBuilder query = select("categories.id", "categories.name")
                    .from("categories")
                    .where("categories.active", "=", l(true))
                    .and(notExists(activeProductsSubquery))
                    .orderBy("categories.name");
            
            String result = query.build();
            
            String expected = "SELECT categories.id, categories.name FROM categories WHERE categories.active = true AND NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = categories.id AND p.active = true) ORDER BY categories.name";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find customers who have profiles but no recent orders")
        void shouldFindCustomersWhoHaveProfilesButNoRecentOrders() {
            SQLBuilder profileSubquery = select("1")
                    .from("profiles")
                    .where("user_id", "=", c("users.id"));
            
            SQLBuilder recentOrdersSubquery = select("1")
                    .from("orders")
                    .where("user_id", "=", c("users.id"))
                    .and("created_at", ">=", l("2024-01-01"));
            
            SQLBuilder query = select("users.id", "users.name", "users.email")
                    .from("users")
                    .where("users.active", "=", l(true))
                    .and(exists(profileSubquery))
                    .and(notExists(recentOrdersSubquery))
                    .orderBy("users.name");
            
            String result = query.build();
            
            String expected = "SELECT users.id, users.name, users.email FROM users WHERE users.active = true AND EXISTS (SELECT 1 FROM profiles WHERE user_id = users.id) AND NOT EXISTS (SELECT 1 FROM orders WHERE user_id = users.id AND created_at >= '2024-01-01') ORDER BY users.name";
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should find orphaned records")
        void shouldFindOrphanedRecords() {
            SQLBuilder query = select("oi.id", "oi.product_id", "oi.quantity")
                    .from("order_items oi")
                    .where(notExists("SELECT 1 FROM orders o WHERE o.id = oi.order_id"))
                    .or(notExists("SELECT 1 FROM products p WHERE p.id = oi.product_id"));
            
            String result = query.build();
            
            String expected = "SELECT oi.id, oi.product_id, oi.quantity FROM order_items oi WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.id = oi.order_id) OR NOT EXISTS (SELECT 1 FROM products p WHERE p.id = oi.product_id)";
            assertEquals(expected, result);
        }
    }
}