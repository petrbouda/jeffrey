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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCacheHintTest {

    /** Lists are fixed for the process; an hour is short enough that a restart with other families shows. */
    @Test
    void staticListsAreSharedForAnHour() {
        ObjectNode result = McpJson.createObject();
        McpCacheHint.STATIC.writeTo(result);

        assertEquals(3_600_000L, result.get("ttlMs").asLong());
        assertEquals("public", result.get("cacheScope").asString());
    }

    /** A {@code jeffrey://} read describes a profile that can change or disappear under the client. */
    @Test
    void dynamicReadsAreNeitherCachedNorShared() {
        ObjectNode result = McpJson.createObject();
        McpCacheHint.DYNAMIC.writeTo(result);

        assertEquals(0L, result.get("ttlMs").asLong());
        assertEquals("private", result.get("cacheScope").asString());
    }

    @Test
    void noneWritesNothing() {
        ObjectNode result = McpJson.createObject();
        McpCacheHint.NONE.writeTo(result);

        assertTrue(result.isEmpty());
        assertFalse(McpCacheHint.NONE.present());
        assertTrue(McpCacheHint.STATIC.present());
    }

    /** A resource read that says nothing about its lifetime is not to be reused. */
    @Test
    void resourceContentsDefaultToDynamic() {
        McpResourceProvider.Contents contents =
                new McpResourceProvider.Contents("jeffrey://profiles", McpResource.TEXT_MARKDOWN, "text");

        assertEquals(McpCacheHint.DYNAMIC, contents.cacheHint());
    }

    @Test
    void resourceContentsKeepTheHintTheyAreGiven() {
        McpResourceProvider.Contents contents = new McpResourceProvider.Contents(
                "jeffrey://server", McpResource.APPLICATION_JSON, "{}", McpCacheHint.STATIC);

        assertEquals(McpCacheHint.STATIC, contents.cacheHint());
        assertThrows(NullPointerException.class, () -> new McpResourceProvider.Contents(
                "jeffrey://server", McpResource.APPLICATION_JSON, "{}", null));
    }

    @Test
    void refusesANegativeLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new McpCacheHint(-1, McpCacheHint.Scope.PUBLIC));
    }
}
