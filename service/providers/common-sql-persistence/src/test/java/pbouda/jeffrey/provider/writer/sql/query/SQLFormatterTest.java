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

package pbouda.jeffrey.provider.writer.sql.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLFormatterTest {

    private final TestSQLFormatter sqlParts = new TestSQLFormatter();

    @Nested
    @DisplayName("formatJson method")
    class FormatJsonTest {

        @Test
        @DisplayName("Should convert simple column::jsonb to json(column)")
        void shouldConvertSimpleColumnJsonb() {
            String input = "SELECT fields::jsonb FROM table";
            String expected = "SELECT json(fields) FROM table";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should convert qualified column::jsonb to json(qualified.column)")
        void shouldConvertQualifiedColumnJsonb() {
            String input = "SELECT events.fields::jsonb FROM events";
            String expected = "SELECT json(events.fields) FROM events";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle multiple jsonb columns in one query")
        void shouldHandleMultipleJsonbColumns() {
            String input = "SELECT events.fields::jsonb, metadata.info::jsonb FROM events";
            String expected = "SELECT json(events.fields), json(metadata.info) FROM events";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle column with alias")
        void shouldHandleColumnWithAlias() {
            String input = "SELECT events.fields::jsonb AS event_fields FROM events";
            String expected = "SELECT json(events.fields) AS event_fields FROM events";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle complex query with multiple clauses")
        void shouldHandleComplexQuery() {
            String input = """
                SELECT
                    events.event_type,
                    events.start_timestamp,
                    events.start_timestamp_from_beginning,
                    events.duration,
                    events.samples,
                    events.weight,
                    events.weight_entity,
                    events.fields::jsonb AS event_fields
                FROM events
                WHERE events.profile_id = :profile_id AND events.event_type = :event_type
                ORDER BY events.start_timestamp_from_beginning DESC LIMIT 1""";

            String expected = """
                SELECT
                    events.event_type,
                    events.start_timestamp,
                    events.start_timestamp_from_beginning,
                    events.duration,
                    events.samples,
                    events.weight,
                    events.weight_entity,
                    json(events.fields) AS event_fields
                FROM events
                WHERE events.profile_id = :profile_id AND events.event_type = :event_type
                ORDER BY events.start_timestamp_from_beginning DESC LIMIT 1""";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle deeply qualified column names")
        void shouldHandleDeeplyQualifiedColumnNames() {
            String input = "SELECT schema.table.column::jsonb FROM schema.table";
            String expected = "SELECT json(schema.table.column) FROM schema.table";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle column names with underscores")
        void shouldHandleColumnNamesWithUnderscores() {
            String input = "SELECT event_data.json_fields::jsonb FROM event_data";
            String expected = "SELECT json(event_data.json_fields) FROM event_data";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should not modify SQL without jsonb columns")
        void shouldNotModifySqlWithoutJsonbColumns() {
            String input = "SELECT events.event_type, events.start_timestamp FROM events WHERE events.profile_id = 'test'";
            String expected = "SELECT events.event_type, events.start_timestamp FROM events WHERE events.profile_id = 'test'";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle empty string")
        void shouldHandleEmptyString() {
            String input = "";
            String expected = "";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Should handle SQL with existing json() function calls")
        void shouldHandleSqlWithExistingJsonFunctionCalls() {
            String input = "SELECT json(events.data), events.fields::jsonb FROM events";
            String expected = "SELECT json(events.data), json(events.fields) FROM events";

            String result = sqlParts.formatJson(input);

            assertEquals(expected, result);
        }
    }
}
