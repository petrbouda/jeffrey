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
import static pbouda.jeffrey.sql.criteria.SqlCriteria.l;

class SqlCriteriaColumnTest {

    @Nested
    @DisplayName("Single Column Addition Tests")
    class SingleColumnAdditionTests {

        @Test
        @DisplayName("Should add single column to empty criteria")
        void shouldAddSingleColumnToEmptyCriteria() {
            SqlCriteria criteria = new SqlCriteria();
            
            criteria.addColumn("name").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name FROM users", result);
        }

        @Test
        @DisplayName("Should add single column to existing criteria")
        void shouldAddSingleColumnToExistingCriteria() {
            SqlCriteria criteria = SqlCriteria.select("id");
            
            criteria.addColumn("name").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id, name FROM users", result);
        }

        @Test
        @DisplayName("Should trim whitespace from column names")
        void shouldTrimWhitespaceFromColumnNames() {
            SqlCriteria criteria = new SqlCriteria();
            
            criteria.addColumn("  name  ").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name FROM users", result);
        }

        @Test
        @DisplayName("Should support complex column expressions")
        void shouldSupportComplexColumnExpressions() {
            SqlCriteria criteria = new SqlCriteria();
            
            criteria.addColumn("COUNT(*) as total")
                    .addColumn("AVG(salary) as avg_salary")
                    .from("employees");
            
            String result = criteria.build();
            assertEquals("SELECT COUNT(*) as total, AVG(salary) as avg_salary FROM employees", result);
        }

        @Test
        @DisplayName("Should support qualified column names")
        void shouldSupportQualifiedColumnNames() {
            SqlCriteria criteria = new SqlCriteria();
            
            criteria.addColumn("u.name")
                    .addColumn("u.email")
                    .from("users u");
            
            String result = criteria.build();
            assertEquals("SELECT u.name, u.email FROM users u", result);
        }

        @Test
        @DisplayName("Should return same instance for method chaining")
        void shouldReturnSameInstanceForMethodChaining() {
            SqlCriteria criteria = new SqlCriteria();
            
            SqlCriteria result = criteria.addColumn("name");
            
            assertSame(criteria, result);
        }
    }

    @Nested
    @DisplayName("Multiple Column Addition Tests")
    class MultipleColumnAdditionTests {

        @Test
        @DisplayName("Should add multiple columns via varargs")
        void shouldAddMultipleColumnsViaVarargs() {
            SqlCriteria criteria = new SqlCriteria();
            
            criteria.addColumns("name", "email", "age").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name, email, age FROM users", result);
        }

        @Test
        @DisplayName("Should add multiple columns via list")
        void shouldAddMultipleColumnsViaList() {
            SqlCriteria criteria = new SqlCriteria();
            List<String> columns = Arrays.asList("name", "email", "age");
            
            criteria.addColumns(columns).from("users");
            
            String result = criteria.build();
            assertEquals("SELECT name, email, age FROM users", result);
        }

        @Test
        @DisplayName("Should add columns to existing select")
        void shouldAddColumnsToExistingSelect() {
            SqlCriteria criteria = SqlCriteria.select("id");
            
            criteria.addColumns("name", "email").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id, name, email FROM users", result);
        }

        @Test
        @DisplayName("Should handle empty varargs")
        void shouldHandleEmptyVarargs() {
            SqlCriteria criteria = SqlCriteria.select("id");
            
            criteria.addColumns().from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id FROM users", result);
        }

        @Test
        @DisplayName("Should handle empty list")
        void shouldHandleEmptyList() {
            SqlCriteria criteria = SqlCriteria.select("id");
            
            criteria.addColumns(Arrays.asList()).from("users");
            
            String result = criteria.build();
            assertEquals("SELECT id FROM users", result);
        }

        @Test
        @DisplayName("Should preserve order of columns")
        void shouldPreserveOrderOfColumns() {
            SqlCriteria criteria = new SqlCriteria();
            
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
            SqlCriteria criteria = SqlCriteria.select("id", "name", "email");
            
            criteria.clearColumns().addColumn("username").from("users");
            
            String result = criteria.build();
            assertEquals("SELECT username FROM users", result);
        }

        @Test
        @DisplayName("Should get copy of current columns")
        void shouldGetCopyOfCurrentColumns() {
            SqlCriteria criteria = SqlCriteria.select("id", "name");
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
            SqlCriteria criteria = SqlCriteria.select("id", "name");
            
            List<String> columns = criteria.getColumns();
            columns.add("modified");
            
            List<String> originalColumns = criteria.getColumns();
            assertEquals(2, originalColumns.size());
            assertFalse(originalColumns.contains("modified"));
        }

        @Test
        @DisplayName("Should check if has columns")
        void shouldCheckIfHasColumns() {
            SqlCriteria criteria = new SqlCriteria();
            
            assertFalse(criteria.hasColumns());
            
            criteria.addColumn("name");
            assertTrue(criteria.hasColumns());
            
            criteria.clearColumns();
            assertFalse(criteria.hasColumns());
        }

        @Test
        @DisplayName("Should handle clearing empty columns")
        void shouldHandleClearingEmptyColumns() {
            SqlCriteria criteria = new SqlCriteria();
            
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
            SqlCriteria criteria = new SqlCriteria();
            
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
            SqlCriteria criteria1 = new SqlCriteria();
            criteria1.addColumns("u.name", "u.email").from("users u");
            
            SqlCriteria criteria2 = SqlCriteria.select("o.amount");
            criteria2.addColumn("o.date").from("orders o");
            
            criteria1.merge(criteria2);
            
            String result = criteria1.build();
            assertEquals("SELECT u.name, u.email, o.amount, o.date FROM users u, orders o", result);
        }

        @Test
        @DisplayName("Should allow dynamic column building")
        void shouldAllowDynamicColumnBuilding() {
            SqlCriteria criteria = new SqlCriteria();
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
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn(null)
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for empty column")
        void shouldThrowExceptionForEmptyColumn() {
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn("")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for whitespace-only column")
        void shouldThrowExceptionForWhitespaceOnlyColumn() {
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumn("   ")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null varargs array")
        void shouldThrowExceptionForNullVarargsArray() {
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns((String[]) null)
            );
            
            assertEquals("Columns array cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null list")
        void shouldThrowExceptionForNullList() {
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns((List<String>) null)
            );
            
            assertEquals("Columns list cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null column in varargs")
        void shouldThrowExceptionForNullColumnInVarargs() {
            SqlCriteria criteria = new SqlCriteria();
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns("valid", null, "another")
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for empty column in list")
        void shouldThrowExceptionForEmptyColumnInList() {
            SqlCriteria criteria = new SqlCriteria();
            List<String> columns = Arrays.asList("valid", "", "another");
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> criteria.addColumns(columns)
            );
            
            assertEquals("Column cannot be null or empty", exception.getMessage());
        }
    }
}
