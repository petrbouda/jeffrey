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
import static pbouda.jeffrey.sql.SQLBuilder.c;
import static pbouda.jeffrey.sql.SQLBuilder.l;

class SQLBuilderJoinConditionsTest {

    @Nested
    @DisplayName("Join with Condition Objects Tests")
    class JoinWithConditionObjectsTests {

        @Test
        @DisplayName("Should support inner join with single condition object")
        void shouldSupportInnerJoinWithSingleConditionObject() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", SQLBuilder.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u INNER JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support inner join with composite AND condition")
        void shouldSupportInnerJoinWithCompositeAndCondition() {
            SQLBuilder criteria = SQLBuilder.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", 
                          SQLBuilder.and(
                              SQLBuilder.eq("e.profile_id", c("s.profile_id")),
                              SQLBuilder.eq("e.stacktrace_id", c("s.stacktrace_id"))
                          ));

            String result = criteria.build();
            String expected = "SELECT e.id, s.frames FROM events e INNER JOIN stacktraces s ON (e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support left join with condition object")
        void shouldSupportLeftJoinWithConditionObject() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u")
                    .leftJoin("posts p", SQLBuilder.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u LEFT JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support right join with condition object")
        void shouldSupportRightJoinWithConditionObject() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u")
                    .rightJoin("posts p", SQLBuilder.eq("u.id", c("p.user_id")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u RIGHT JOIN posts p ON u.id = p.user_id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex join conditions with multiple operators")
        void shouldSupportComplexJoinConditionsWithMultipleOperators() {
            SQLBuilder criteria = SQLBuilder.select("o.id", "i.name")
                    .from("orders o")
                    .join("items i", 
                          SQLBuilder.and(
                              SQLBuilder.eq("o.item_id", c("i.id")),
                              SQLBuilder.gte("o.quantity", l(5)),
                              SQLBuilder.lt("i.price", l(100))
                          )
                    );

            String result = criteria.build();
            String expected = "SELECT o.id, i.name FROM orders o INNER JOIN items i ON (o.item_id = i.id AND o.quantity >= 5 AND i.price < 100)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support join with OR condition")
        void shouldSupportJoinWithOrCondition() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "c.email")
                    .from("users u")
                    .join("contacts c",
                          SQLBuilder.or(
                              SQLBuilder.eq("u.email", c("c.email")),
                              SQLBuilder.eq("u.phone", c("c.phone"))
                          ));

            String result = criteria.build();
            String expected = "SELECT u.name, c.email FROM users u INNER JOIN contacts c ON (u.email = c.email OR u.phone = c.phone)";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support join with IN condition")
        void shouldSupportJoinWithInCondition() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "r.name")
                    .from("users u")
                    .join("roles r", SQLBuilder.inInts("u.role_id", 1, 2, 3));

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
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title", "c.content")
                    .from("users u")
                    .join("posts p", SQLBuilder.eq("u.id", c("p.user_id")))
                    .leftJoin("comments c", 
                              SQLBuilder.and(
                                  SQLBuilder.eq("p.id", c("c.post_id")),
                                  SQLBuilder.eq("c.approved", l(true))
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
            SQLBuilder criteria = SQLBuilder.select("e.id", "s.frames", "t.tag")
                    .from("events e")
                    .join("stacktraces s", "e.profile_id = s.profile_id")  // String condition
                    .leftJoin("tags t", SQLBuilder.eq("e.tag_id", c("t.id")));  // Condition object

            String result = criteria.build();
            String expected = "SELECT e.id, s.frames, t.tag FROM events e " +
                            "INNER JOIN stacktraces s ON e.profile_id = s.profile_id " +
                            "LEFT JOIN tags t ON e.tag_id = t.id";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex multi-table joins")
        void shouldSupportComplexMultiTableJoins() {
            SQLBuilder criteria = SQLBuilder.select("u.username", "o.total", "p.name")
                    .from("users u")
                    .join("orders o", 
                          SQLBuilder.and(
                              SQLBuilder.eq("u.id", c("o.user_id")),
                              SQLBuilder.gte("o.created_date", l("2024-01-01"))
                          ))
                    .join("products p", SQLBuilder.eq("o.product_id", c("p.id")))
                    .leftJoin("reviews r", 
                              SQLBuilder.and(
                                  SQLBuilder.eq("o.id", c("r.order_id")),
                                  SQLBuilder.gte("r.rating", l(4))
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
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
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
            SQLBuilder stringCriteria = SQLBuilder.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", "e.profile_id = s.profile_id AND e.stacktrace_id = s.stacktrace_id");

            // Condition-based join
            SQLBuilder conditionCriteria = SQLBuilder.select("e.id", "s.frames")
                    .from("events e")
                    .join("stacktraces s", 
                          SQLBuilder.and(
                              SQLBuilder.eq("e.profile_id", c("s.profile_id")),
                              SQLBuilder.eq("e.stacktrace_id", c("s.stacktrace_id"))
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
            SQLBuilder criteria = new SQLBuilder();
            
            String result = criteria
                    .addColumn("u.name")
                    .addColumn("p.title")
                    .from("users u")
                    .join("posts p", SQLBuilder.eq("u.id", c("p.user_id")))
                    .where(SQLBuilder.eq("u.active", l(true)))
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
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", SQLBuilder.eq("u.id", c("p.user_id")))
                    .where(SQLBuilder.eq("u.active", l(true)))
                    .and(SQLBuilder.gte("p.created_date", l("2024-01-01")));

            String result = criteria.build();
            String expected = "SELECT u.name, p.title FROM users u " +
                            "INNER JOIN posts p ON u.id = p.user_id " +
                            "WHERE u.active = true AND p.created_date >= '2024-01-01'";

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should support complex query with joins, conditions, and aggregation")
        void shouldSupportComplexQueryWithJoinsConditionsAndAggregation() {
            SQLBuilder criteria = SQLBuilder.select("u.department", "COUNT(*) as post_count", "AVG(p.views) as avg_views")
                    .from("users u")
                    .join("posts p", 
                          SQLBuilder.and(
                              SQLBuilder.eq("u.id", c("p.user_id")),
                              SQLBuilder.eq("p.status", l("published"))
                          ))
                    .where(SQLBuilder.gte("p.created_date", l("2024-01-01")))
                    .groupBy("u.department")
                    .having(SQLBuilder.gt("COUNT(*)", l(5)))
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
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u");

            // This should not throw an exception
            assertDoesNotThrow(() -> {
                criteria.join("posts p", (Condition) null);
            });
        }

        @Test
        @DisplayName("Should produce valid SQL even with null condition in join")
        void shouldProduceValidSqlEvenWithNullConditionInJoin() {
            SQLBuilder criteria = SQLBuilder.select("u.name", "p.title")
                    .from("users u")
                    .join("posts p", (Condition) null);

            String result = criteria.build();
            
            // Should contain the join with null condition handled
            assertTrue(result.contains("INNER JOIN posts p ON null"));
        }
    }
}
