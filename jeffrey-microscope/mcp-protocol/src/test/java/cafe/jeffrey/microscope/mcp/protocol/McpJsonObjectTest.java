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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpJsonObjectTest {

    record Carrier(String tool, McpJsonObject arguments) {
    }

    @Nested
    class Serialisation {

        @Test
        void serialisesAsThePlainObjectItWraps() {
            ObjectNode arguments = McpJson.createObject().put("profileId", "p-1").put("limit", 5);

            String json = McpJson.toString(new Carrier("profiles_evidence", new McpJsonObject(arguments)));

            assertEquals("{\"tool\":\"profiles_evidence\",\"arguments\":{\"profileId\":\"p-1\",\"limit\":5}}", json);
        }

        @Test
        void buildsFromNamedValuesInTheirOrder() {
            McpJsonObject object = McpJsonObject.of(Map.of("settings", Map.of("period", "10 ms")));

            assertEquals(McpJson.readTree("{\"settings\":{\"period\":\"10 ms\"}}"), McpJson.toTree(object));
        }

        @Test
        void keepsNestedCollections() {
            McpJsonObject object = McpJsonObject.of(Map.of("ids", List.of("a", "b")));

            assertEquals("b", McpJson.toTree(object).path("ids").get(1).asString());
        }
    }

    @Nested
    class Immutability {

        @Test
        void copiesTheNodeItIsGiven() {
            ObjectNode source = McpJson.createObject().put("count", 3);
            McpJsonObject object = new McpJsonObject(source);

            source.put("count", 9);

            assertEquals(3, object.value().path("count").asInt());
        }

        @Test
        void handsOutACopyOnEveryRead() {
            McpJsonObject object = new McpJsonObject(McpJson.createObject().put("count", 3));

            object.value().put("count", 10);

            assertEquals(3, object.value().path("count").asInt());
            assertNotSame(object.value(), object.value());
        }

        @Test
        void refusesNull() {
            assertThrows(NullPointerException.class, () -> new McpJsonObject(null));
        }
    }

    @Test
    void twoObjectsWithTheSameContentAreEqual() {
        JsonNode node = McpJson.readTree("{\"a\":1}");

        assertEquals(new McpJsonObject((ObjectNode) node), new McpJsonObject(McpJson.createObject().put("a", 1)));
    }
}
