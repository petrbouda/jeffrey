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
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpServerIdentityTest {

    @Test
    void refusesAMissingVersion() {
        assertThrows(NullPointerException.class, () -> new McpServerIdentity("example", null));
    }

    @Test
    void rendersTheImplementationObject() {
        ObjectNode json = new McpServerIdentity("example", "1.2.3").toJson();

        assertEquals("example", json.get("name").asString());
        assertEquals("1.2.3", json.get("version").asString());
        assertEquals(2, json.size());
    }

    @Test
    void refusesABlankName() {
        assertThrows(IllegalArgumentException.class, () -> new McpServerIdentity(" ", "1"));
    }
}
