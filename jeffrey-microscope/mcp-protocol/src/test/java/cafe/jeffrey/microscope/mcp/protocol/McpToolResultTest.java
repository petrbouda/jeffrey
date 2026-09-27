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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a result measures itself against a server's limit. */
class McpToolResultTest {

    private static final ObjectNode ROWS = McpJson.createObject().put("rows", "x".repeat(10));

    /** {@code {"rows":"xxxxxxxxxx"}} is 21 characters: the limit is inclusive. */
    @Test
    void exceedsOnlyPastTheLimit() {
        McpToolResult result = new McpToolResult("rows", ROWS);

        assertEquals(21, McpJson.toString(ROWS).length());
        assertFalse(result.exceeds(21));
        assertTrue(result.exceeds(20));
    }

    @Test
    void aTextOnlyResultNeverExceeds() {
        assertFalse(McpToolResult.text("x".repeat(100)).exceeds(1));
    }

    @Test
    void namesTheCutTheCallerCanMake() {
        assertEquals("Structured tool result exceeds the output size limit. "
                + "Return fewer rows, or narrow the query that produced them.", McpToolResult.OVERSIZED_STRUCTURED_CONTENT);
    }
}
