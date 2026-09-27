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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientCapabilitiesTest {

    @Nested
    class Elicitation {

        /** An empty {@code elicitation} object predates the modes and means form. */
        @Test
        void readsAnEmptyElicitationAsForm() {
            assertTrue(parse("{\"elicitation\":{}}").elicitationForm());
        }

        @Test
        void readsAnExplicitFormMode() {
            assertTrue(parse("{\"elicitation\":{\"form\":{}}}").elicitationForm());
        }

        @Test
        void doesNotReadUrlModeAsForm() {
            assertFalse(parse("{\"elicitation\":{\"url\":{}}}").elicitationForm());
        }

        @Test
        void readsNoElicitationAsNone() {
            assertFalse(parse("{}").elicitationForm());
        }
    }

    @Nested
    class Extensions {

        @Test
        void readsTheDeclaredExtensionIds() {
            McpClientCapabilities capabilities = parse("{\"extensions\":{\"" + McpClientCapabilities.TASKS_EXTENSION
                    + "\":{},\"" + McpClientCapabilities.SKILLS_EXTENSION + "\":{}}}");

            assertEquals(
                    Set.of("io.modelcontextprotocol/tasks", "io.modelcontextprotocol/skills"),
                    capabilities.extensions());
        }

        @Test
        void readsNoExtensionsAsEmpty() {
            assertTrue(parse("{}").extensions().isEmpty());
        }
    }

    @Nested
    class Shape {

        @Test
        void refusesCapabilitiesThatAreNotAnObject() {
            McpProtocolException e = assertThrows(McpProtocolException.class,
                    () -> McpClientCapabilities.parse(McpJson.readTree("[]")));

            assertEquals(McpErrorCode.INVALID_META, e.code());
        }

        @Test
        void keepsTheRawDeclarationAsACopy() {
            ObjectNode declared = (ObjectNode) McpJson.readTree("{\"roots\":{}}");
            McpClientCapabilities capabilities = McpClientCapabilities.parse(declared);
            declared.put("sampling", true);

            assertFalse(capabilities.raw().has("sampling"));
            assertTrue(capabilities.raw().has("roots"));
        }

        @Test
        void declaresNothingWhenNone() {
            assertFalse(McpClientCapabilities.NONE.elicitationForm());
            assertTrue(McpClientCapabilities.NONE.extensions().isEmpty());
            assertTrue(McpClientCapabilities.NONE.raw().isEmpty());
        }
    }

    private static McpClientCapabilities parse(String json) {
        return McpClientCapabilities.parse(McpJson.readTree(json));
    }
}
