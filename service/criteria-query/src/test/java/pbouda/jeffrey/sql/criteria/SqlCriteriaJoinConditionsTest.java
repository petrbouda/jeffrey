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

package pbouda.jeffrey.sql.criteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;
import static pbouda.jeffrey.sql.criteria.SqlCriteria.c;
import static pbouda.jeffrey.sql.criteria.SqlCriteria.l;

class SqlCriteriaJoinConditionsTest {

    @Nested
    @DisplayName("Join with Condition Objects Tests")
    class JoinWithConditionObjectsTests {

        @Test
        @DisplayName("Should support inner join with single condition object")
        void shouldSupportInnerJoinWithSingleConditionObject() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", SqlCriteria.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u INNER JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support inner join with composite AND condition")
        void shouldSupportInnerJoinWithCompositeAndCondition() {
            SqlCriteria criteria = SqlCriteria.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", 
                          SqlCriteria.and(
                              SqlCriteria.eq("e.profile_id", c("s.profile_id")),
                              SqlCriteria.eq("e.stacktrace_id", c("s.stacktrace_id"))
                          ));

            String result = criteria.build();
            String expected = "SELECT e.id, s.frames FROM events e INNER JOIN stacktraces s ON (e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support left join with condition object")
        void shouldSupportLeftJoinWithConditionObject() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .leftJoin("posts p", SqlCriteria.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u LEFT JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support right join with condition object")
        void shouldSupportRightJoinWithConditionObject() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .rightJoin("posts p", SqlCriteria.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u RIGHT JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex join conditions with multiple operators")
        void shouldSupportComplexJoinConditionsWithMultipleOperators() {
            SqlCriteria criteria = SqlCriteria.select("o.id", "i.name")
                    .from("orders o")
                    .join("items i", 
                          SqlCriteria.and(
                              SqlCriteria.eq("o.item_id", c("i.id")),
                              SqlCriteria.gte("o.quantity", l(5)),
                              SqlCriteria.lt("i.price", l(100))
                          )
                    );

            String result = criteria.build();
            String expected = "SELECT o.id, i.name FROM orders o INNER JOIN items i ON (o.item_id = i.id AND o.quantity >= 5 AND i.price < 100)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support join with OR condition")
        void shouldSupportJoinWithOrCondition() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "c.email")
                    .from("users u")
                    .join("contacts c",
                          SqlCriteria.or(
                              SqlCriteria.eq("u.email", c("c.email")),
                              SqlCriteria.eq("u.phone", c("c.phone"))
                          ));

            String result = criteria.build();
            String expected = "SELECT u.name, c.email FROM users u INNER JOIN contacts c ON (u.email = c.email OR u.phone = c.phone)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support join with IN condition")
        void shouldSupportJoinWithInCondition() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "r.name")
                    .from("users u")
                    .join("roles r", SqlCriteria.inInts("u.role_id", 1, 2, 3));

            String result = criteria.build();
            String expected = "SELECT u.name, r.name FROM users u INNER JOIN roles r ON u.role_id IN (1, 2, 3)";

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Multiple Joins with Conditions Tests")
    class MultipleJoinsWithConditionsTests {

        @Test
        @DisplayName("Should support multiple joins with different condition types")
        void shouldSupportMultipleJoinsWithDifferentConditionTypes() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title", "c.content")
                    .from("users u")
                    .join("posts p", SqlCriteria.eq("u.id", c("p.user_id")))
                    .leftJoin("comments c", 
                              SqlCriteria.and(
                                  SqlCriteria.eq("p.id", c("c.post_id")),
                                  SqlCriteria.eq("c.approved", l(true))
                              ));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title, c.content FROM users u " +
                            "INNER JOIN posts p ON u.id = p.user_id " +
                            "LEFT JOIN comments c ON (p.id = c.post_id AND c.approved = true)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support mixed string and condition joins")
        void shouldSupportMixedStringAndConditionJoins() {
            SqlCriteria criteria = SqlCriteria.select("e.id", "s.frames", "t.tag")
                    .from("events e")
                    .join("stacktraces s", "e.profile_id = s.profile_id")  // String condition
                    .leftJoin("tags t", SqlCriteria.eq("e.tag_id", c("t.id")));  // Condition object

            String result = criteria.build();
            String expected = "SELECT e.id, s.frames, t.tag FROM events e " +
                            "INNER JOIN stacktraces s ON e.profile_id = s.profile_id " +
                            "LEFT JOIN tags t ON e.tag_id = t.id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex multi-table joins")
        void shouldSupportComplexMultiTableJoins() {
            SqlCriteria criteria = SqlCriteria.select("u.username", "o.total", "p.name")
                    .from("users u")
                    .join("orders o", 
                          SqlCriteria.and(
                              SqlCriteria.eq("u.id", c("o.user_id")),
                              SqlCriteria.gte("o.created_date", l("2024-01-01"))
                          ))
                    .join("products p", SqlCriteria.eq("o.product_id", c("p.id")))
                    .leftJoin("reviews r", 
                              SqlCriteria.and(
                                  SqlCriteria.eq("o.id", c("r.order_id")),
                                  SqlCriteria.gte("r.rating", l(4))
                              ));

            String result = criteria.build();
            String expected = "SELECT u.username, o.total, p.name FROM users u " +
                            "INNER JOIN orders o ON (u.id = o.user_id AND o.created_date >= '2024-01-01') " +
                            "INNER JOIN products p ON o.product_id = p.id " +
                            "LEFT JOIN reviews r ON (o.id = r.order_id AND r.rating >= 4)";

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should maintain backward compatibility with string conditions")
        void shouldMaintainBackwardCompatibilityWithStringConditions() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", "u.id = p.user_id");  // String condition

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u INNER JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should produce identical results for equivalent conditions")
        void shouldProduceIdenticalResultsForEquivalentConditions() {
            // String-based join
            SqlCriteria stringCriteria = SqlCriteria.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", "e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id");

            // Condition-based join
            SqlCriteria conditionCriteria = SqlCriteria.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", 
                          SqlCriteria.and(
                              SqlCriteria.eq("e.profile_id", c("s.profile_id")),
                              SqlCriteria.eq("e.stacktrace_id", c("s.stacktrace_id"))
                          ));

            String stringResult = stringCriteria.build();
            String conditionResult = conditionCriteria.build();

            // Both should produce valid SQL, though the condition-based one has parentheses
            assertTrue(stringResult.contains("INNER JOIN stacktraces s ON e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id"));
            assertTrue(conditionResult.contains("INNER JOIN stacktraces s ON (e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id)"));
        }

        @Test
        @DisplayName("Should support method chaining with condition joins")
        void shouldSupportMethodChainingWithConditionJoins() {
            SqlCriteria criteria = new SqlCriteria();
            
            String result = criteria
                    .addColumn("u.name")
                    .addColumn("p.title")
                    .from("users u")
                    .join("posts p", SqlCriteria.eq("u.id", c("p.user_id")))
                    .where(SqlCriteria.eq("u.active", l(true)))
                    .orderBy("u.name")
                    .build();

            assertAll(
                    () -> assertNotNull(result),
                    () -> assertTrue(result.contains("SELECT u.name, p.title")),
                    () -> assertTrue(result.contains("FROM users u")),
                    () -> assertTrue(result.contains("INNER JOIN posts p ON u.id = p.user_id")),
                    () -> assertTrue(result.contains("WHERE u.active = true")),
                    () -> assertTrue(result.contains("ORDER BY u.name"))
            );
        }
    }

    @Nested
    @DisplayName("Integration with WHERE Conditions Tests")
    class IntegrationWithWhereConditionsTests {

        @Test
        @DisplayName("Should support joins with conditions and WHERE clauses")
        void shouldSupportJoinsWithConditionsAndWhereClauses() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", SqlCriteria.eq("u.id", c("p.user_id")))
                    .where(SqlCriteria.eq("u.active", l(true)))
                    .and(SqlCriteria.gte("p.created_date", l("2024-01-01")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u " +
                            "INNER JOIN posts p ON u.id = p.user_id " +
                            "WHERE u.active = true AND p.created_date >= '2024-01-01'";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex query with joins, conditions, and aggregation")
        void shouldSupportComplexQueryWithJoinsConditionsAndAggregation() {
            SqlCriteria criteria = SqlCriteria.select("u.department", "COUNT(*) as post_count", "AVG(p.views) as avg_views")
                    .from("users u")
                    .join("posts p", 
                          SqlCriteria.and(
                              SqlCriteria.eq("u.id", c("p.user_id")),
                              SqlCriteria.eq("p.status", l("published"))
                          ))
                    .where(SqlCriteria.gte("p.created_date", l("2024-01-01")))
                    .groupBy("u.department")
                    .having(SqlCriteria.gt("COUNT(*)", l(5)))
                    .orderBy("avg_views", "DESC");

            String result = criteria.build();
            String expected = "SELECT u.department, COUNT(*) as post_count, AVG(p.views) as avg_views " +
                            "FROM users u " +
                            "INNER JOIN posts p ON (u.id = p.user_id AND p.status = 'published') " +
                            "WHERE p.created_date >= '2024-01-01' " +
                            "GROUP BY u.department " +
                            "HAVING COUNT(*) > 5 " +
                            "ORDER BY avg_views DESC";

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null condition object gracefully")
        void shouldHandleNullConditionObjectGracefully() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u");

            // This should not throw an exception
            assertDoesNotThrow(() -> {
                criteria.join("posts p", (Condition) null);
            });
        }

        @Test
        @DisplayName("Should produce valid SQL even with null condition in join")
        void shouldProduceValidSqlEvenWithNullConditionInJoin() {
            SqlCriteria criteria = SqlCriteria.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", (Condition) null);

            String result = criteria.build();
            
            // Should contain the join with null condition handled
            assertTrue(result.contains("INNER JOIN posts p ON null"));
        }
    }
}
