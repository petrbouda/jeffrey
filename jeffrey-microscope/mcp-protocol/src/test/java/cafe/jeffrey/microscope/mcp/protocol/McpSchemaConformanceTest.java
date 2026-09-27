/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.mcp.protocol;

import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The check the tool tests lean on has to fail where a client's validator would. */
class McpSchemaConformanceTest {

    enum Kind {
        A, B
    }

    record Row(String name, @McpNullable Long size, Kind kind, @McpMinimum(0) int count) {
    }

    record Page(List<Row> rows, Map<String, Long> totals, McpJsonObject extra) {
    }

    private static final JsonNode SCHEMA = McpSchemaGenerator.schemaOf(Page.class);

    private static JsonNode valid() {
        return McpJson.toTree(new Page(List.of(new Row("r", null, Kind.A, 1)), Map.of("x", 1L),
                McpJsonObject.of(Map.of("anything", List.of(1, "two")))));
    }

    private static String failure(String json) {
        return assertThrows(AssertionError.class,
                () -> McpSchemaConformance.assertConforms(McpJson.readTree(json), SCHEMA)).getMessage();
    }

    @Test
    void acceptsTheSerialisedRecord() {
        assertDoesNotThrow(() -> McpSchemaConformance.assertConforms(valid(), SCHEMA));
    }

    @Nested
    class Refuses {

        @Test
        void aWrongType() {
            String message = failure("""
                    {"rows":[{"name":7,"size":null,"kind":"A","count":1}],"totals":{},"extra":{}}""");
            assertTrue(message.startsWith("$.rows[0].name is NUMBER"), message);
        }

        @Test
        void aMissingRequiredProperty() {
            String message = failure("""
                    {"rows":[{"name":"r","kind":"A","count":1}],"totals":{},"extra":{}}""");
            assertTrue(message.contains("$.rows[0] is missing the required property size"), message);
        }

        @Test
        void anUndeclaredProperty() {
            String message = failure("""
                    {"rows":[],"totals":{},"extra":{},"_truncated":true}""");
            assertTrue(message.contains("carries _truncated"), message);
        }

        @Test
        void aValueOutsideTheEnum() {
            String message = failure("""
                    {"rows":[{"name":"r","size":1,"kind":"C","count":1}],"totals":{},"extra":{}}""");
            assertTrue(message.contains("not one of"), message);
        }

        @Test
        void aMapValueOfTheWrongType() {
            String message = failure("""
                    {"rows":[],"totals":{"x":"one"},"extra":{}}""");
            assertTrue(message.startsWith("$.totals.x is STRING"), message);
        }

        @Test
        void aValueBelowTheMinimum() {
            String message = failure("""
                    {"rows":[{"name":"r","size":1,"kind":"A","count":-1}],"totals":{},"extra":{}}""");
            assertTrue(message.contains("below the declared minimum"), message);
        }

        @Test
        void aKeywordItDoesNotUnderstand() {
            AssertionError error = assertThrows(AssertionError.class, () -> McpSchemaConformance.assertConforms(
                    McpJson.readTree("{}"), McpJson.readTree("{\"type\":\"object\",\"$ref\":\"#/x\"}")));
            assertTrue(error.getMessage().contains("does not understand '$ref'"), error.getMessage());
        }
    }
}
