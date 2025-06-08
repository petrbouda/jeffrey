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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pbouda.jeffrey.sql.criteria.SQLBuilder.l;

class SQLBuilderColumnTest {

    @Nested
    @DisplayName("Single Column Addition Tests")
    class SingleColumnAdditionTests {

        @Test
        @DisplayName("Should add single column to empty criteria")
        void shouldAddSingleColumnToEmptyCriteria() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("name").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name FROM users", result);
        }

        @Test
        @DisplayName("Should add single column to existing criteria")
        void shouldAddSingleColumnToExistingCriteria() {
            SQLBuilder criteria = SQLBuilder.select("id");
            
            criteria.addColumn("name").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id, name FROM users", result);
        }

        @Test
        @DisplayName("Should trim whitespace from column names")
        void shouldTrimWhitespaceFromColumnNames() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("  name  ").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name FROM users", result);
        }

        @Test
        @DisplayName("Should support complex column expressions")
        void shouldSupportComplexColumnExpressions() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("COUNT(*) as total")
                    .addColumn("AVG(salary) as avg_salary")
                    .from("employees");
            
            String result = criteria.build();
            assertEquals("SELECT COUNT(*) as total, AVG(salary) as avg_salary FROM employees", result);
        }

        @Test
        @DisplayName("Should support qualified column names")
        void shouldSupportQualifiedColumnNames() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("u.name")
                    .addColumn("u.email")
                    .from("users u");
            
            String result = criteria.build();
            assertEquals("SELECT u.name, u.email FROM users u", result);
        }

        @Test
        @DisplayName("Should return same instance for method chaining")
        void shouldReturnSameInstanceForMethodChaining() {
            SQLBuilder criteria = new SQLBuilder();
            
            SQLBuilder result = criteria.addColumn("name");
            
            assertSame(criteria, result);
        }
    }

    @Nested
    @DisplayName("Multiple Column Addition Tests")
    class MultipleColumnAdditionTests {

        @Test
        @DisplayName("Should add multiple columns via varargs")
        void shouldAddMultipleColumnsViaVarargs() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumns("name", "email", "age").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name, email, age FROM users", result);
        }

        @Test
        @DisplayName("Should add multiple columns via list")
        void shouldAddMultipleColumnsViaList() {
            SQLBuilder criteria = new SQLBuilder();
            List<String> columns = Arrays.asList("name", "email", "age");
            
            criteria.addColumns(columns).from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name, email, age FROM users", result);
        }

        @Test
        @DisplayName("Should add columns to existing select")
        void shouldAddColumnsToExistingSelect() {
            SQLBuilder criteria = SQLBuilder.select("id");
            
            criteria.addColumns("name", "email").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id, name, email FROM users", result);
        }

        @Test
        @DisplayName("Should handle empty varargs")
        void shouldHandleEmptyVarargs() {
            SQLBuilder criteria = SQLBuilder.select("id");
            
            criteria.addColumns().from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id FROM users", result);
        }

        @Test
        @DisplayName("Should handle empty list")
        void shouldHandleEmptyList() {
            SQLBuilder criteria = SQLBuilder.select("id");
            
            criteria.addColumns(Arrays.asList()).from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id FROM users", result);
        }

        @Test
        @DisplayName("Should preserve order of columns")
        void shouldPreserveOrderOfColumns() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("first")
                    .addColumns("second", "third")
                    .addColumn("fourth")
                    .from("table");
            
            String result = criteria.build();
            assertEquals("SELECT first, second, third, fourth FROM table", result);
        }
    }

    @Nested
    @DisplayName("Column Management Tests")
    class ColumnManagementTests {

        @Test
        @DisplayName("Should clear all columns")
        void shouldClearAllColumns() {
            SQLBuilder criteria = SQLBuilder.select("id", "name", "email");
            
            criteria.clearColumns().addColumn("username").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT username FROM users", result);
        }

        @Test
        @DisplayName("Should get copy of current columns")
        void shouldGetCopyOfCurrentColumns() {
            SQLBuilder criteria = SQLBuilder.select("id", "name");
            criteria.addColumn("email");
            
            List<String> columns = criteria.getColumns();
            
            assertAll(
                    () -> assertEquals(3, columns.size()),
                    () -> assertEquals("id", columns.get(0)),
                    () -> assertEquals("name", columns.get(1)),
                    () -> assertEquals("email", columns.get(2))
            );
        }

        @Test
        @DisplayName("Should return independent copy of columns")
        void shouldReturnIndependentCopyOfColumns() {
            SQLBuilder criteria = SQLBuilder.select("id", "name");
            
            List<String> columns = criteria.getColumns();
            columns.add("modified");
            
            List<String> originalColumns = criteria.getColumns();
            assertEquals(2, originalColumns.size());
            assertFalse(originalColumns.contains("modified"));
        }

        @Test
        @DisplayName("Should check if has columns")
        void shouldCheckIfHasColumns() {
            SQLBuilder criteria = new SQLBuilder();
            
            assertFalse(criteria.hasColumns());
            
            criteria.addColumn("name");
            assertTrue(criteria.hasColumns());
            
            criteria.clearColumns();
            assertFalse(criteria.hasColumns());
        }

        @Test
        @DisplayName("Should handle clearing empty columns")
        void shouldHandleClearingEmptyColumns() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.clearColumns().from("users");
            
            String result = criteria.build();
            assertEquals("SELECT * FROM users", result);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should work with complex query building")
        void shouldWorkWithComplexQueryBuilding() {
            SQLBuilder criteria = new SQLBuilder();
            
            criteria.addColumn("u.id")
                    .addColumn("u.name")
                    .addColumns("o.amount", "o.status")
                    .from("users u")
                    .join("orders o", "u.id = o.user_id")
                    .where("u.active", "=", l(true))
                    .and("o.amount", ">", l(100))
                    .groupBy("u.id")
                    .orderBy("u.name");
            
            String result = criteria.build();
            String expected = "SELECT u.id, u.name, o.amount, o.status FROM users u " +
                            "INNER JOIN orders o ON u.id = o.user_id " +
                            "WHERE u.active = true AND o.amount > 100 " +
                            "GROUP BY u.id ORDER BY u.name";
            
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should work with merge operations")
        void shouldWorkWithMergeOperations() {
            SQLBuilder criteria1 = new SQLBuilder();
            criteria1.addColumns("u.name", "u.email").from("users u");
            
            SQLBuilder criteria2 = SQLBuilder.select("o.amount");
            criteria2.addColumn("o.date").from("orders o");
            
            criteria1.merge(criteria2);
            
            String result = criteria1.build();
            assertEquals("SELECT u.name, u.email, o.amount, o.date FROM users u, orders o", result);
        }

        @Test
        @DisplayName("Should allow dynamic column building")
        void shouldAllowDynamicColumnBuilding() {
            SQLBuilder criteria = new SQLBuilder();
            criteria.from("users");
            
            // Simulate dynamic column addition based on conditions
            boolean includePersonalInfo = true;
            boolean includeContactInfo = true;
            boolean includeStats = false;
            
            if (includePersonalInfo) {
                criteria.addColumns("name", "age");
            }
            if (includeContactInfo) {
                criteria.addColumns("email", "phone");
            }
            if (includeStats) {
                criteria.addColumns("login_count", "last_login");
            }
            
            String result = criteria.build();
            assertEquals("SELECT name, age, email, phone FROM users", result);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for null column")
        void shouldThrowExceptionForNullColumn() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn(null)
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for empty column")
        void shouldThrowExceptionForEmptyColumn() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn("")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for whitespace-only column")
        void shouldThrowExceptionForWhitespaceOnlyColumn() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn("   ")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null varargs array")
        void shouldThrowExceptionForNullVarargsArray() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns((String[]) null)
            );
            
            assertEquals("Columns array cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null list")
        void shouldThrowExceptionForNullList() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns((List<String>) null)
            );
            
            assertEquals("Columns list cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null column in varargs")
        void shouldThrowExceptionForNullColumnInVarargs() {
            SQLBuilder criteria = new SQLBuilder();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns("valid", null, "another")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for empty column in list")
        void shouldThrowExceptionForEmptyColumnInList() {
            SQLBuilder criteria = new SQLBuilder();
            List<String> columns = Arrays.asList("valid", "", "another");
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns(columns)
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }
    }
}
