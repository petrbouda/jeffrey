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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code _meta} a tool carries in {@code tools/list}: whatever keys the server gives it, in the order
 * it gives them, and never a broken entry.
 */
class McpToolSpecTest {

    private static final String SIZE_KEY = "example/maxResultSizeChars";
    private static final String COST_KEY = "example/cost";
    private static final String REQUIRES_KEY = "example/requires";

    private static McpToolSpec spec(Map<String, JsonNode> meta) {
        return new McpToolSpec("test_echo", "Test: Echo", "description", McpJson.createObject(),
                McpToolAnnotations.READ_ONLY, null, meta);
    }

    @Test
    void takesTheTitleAsTheServerGivesIt() {
        assertEquals("Test: Echo", spec(null).title());
        assertNull(new McpToolSpec("test_echo", null, "description", McpJson.createObject(),
                McpToolAnnotations.READ_ONLY, null, null).title());
    }

    @Test
    void aSpecWithoutMetaCarriesAnEmptyMap() {
        assertTrue(spec(null).meta().isEmpty());
    }

    @Test
    void keepsTheMetaItWasGivenAndCannotBeChangedAfterwards() {
        Map<String, JsonNode> meta = new HashMap<>();
        meta.put(SIZE_KEY, IntNode.valueOf(1000));
        McpToolSpec spec = spec(meta);

        meta.clear();

        assertEquals(1000, spec.meta().get(SIZE_KEY).asInt());
        assertThrows(UnsupportedOperationException.class, () -> spec.meta().clear());
    }

    /** The key order is fixed, so tools/list renders the same bytes on every run. */
    @Test
    void keepsTheOrderOfTheMetaItWasGiven() {
        Map<String, JsonNode> meta = new LinkedHashMap<>();
        meta.put(REQUIRES_KEY, McpJson.createArray().add("TRACES"));
        meta.put(SIZE_KEY, IntNode.valueOf(1000));
        meta.put(COST_KEY, StringNode.valueOf("CHEAP"));

        assertEquals(List.of(REQUIRES_KEY, SIZE_KEY, COST_KEY), List.copyOf(spec(meta).meta().keySet()));
    }

    /** A null key or value would render as a broken {@code _meta} entry; it is refused, by name. */
    @Test
    void refusesANullMetaKeyOrValue() {
        Map<String, JsonNode> nullValue = new LinkedHashMap<>();
        nullValue.put(COST_KEY, null);
        Map<String, JsonNode> nullKey = new LinkedHashMap<>();
        nullKey.put(null, StringNode.valueOf("CHEAP"));

        NullPointerException value = assertThrows(NullPointerException.class, () -> spec(nullValue));
        NullPointerException key = assertThrows(NullPointerException.class, () -> spec(nullKey));

        assertTrue(value.getMessage().contains(COST_KEY), value.getMessage());
        assertTrue(key.getMessage().contains("meta key"), key.getMessage());
    }
}
