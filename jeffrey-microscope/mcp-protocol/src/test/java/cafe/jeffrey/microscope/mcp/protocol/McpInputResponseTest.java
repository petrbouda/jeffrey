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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpInputResponseTest {

    @Test
    void readsAnAcceptedFormWithItsContent() {
        McpInputResponse response = McpInputResponse.parse(
                McpJson.readTree("{\"action\":\"accept\",\"content\":{\"confirm\":true}}"));

        assertEquals(McpInputResponse.Action.ACCEPT, response.action());
        assertTrue(response.content().get("confirm").asBoolean());
    }

    @Test
    void readsADeclineWithoutContent() {
        McpInputResponse response = McpInputResponse.parse(McpJson.readTree("{\"action\":\"decline\"}"));

        assertEquals(McpInputResponse.Action.DECLINE, response.action());
        assertNull(response.content());
    }

    @Test
    void readsACancel() {
        assertEquals(McpInputResponse.Action.CANCEL,
                McpInputResponse.parse(McpJson.readTree("{\"action\":\"cancel\"}")).action());
    }

    @Test
    void refusesAnActionItDoesNotKnow() {
        McpProtocolException e = assertThrows(McpProtocolException.class,
                () -> McpInputResponse.parse(McpJson.readTree("{\"action\":\"maybe\"}")));

        assertEquals(McpErrorCode.INVALID_PARAMS, e.code());
    }

    @Test
    void refusesContentThatIsNotAnObject() {
        assertThrows(McpProtocolException.class,
                () -> McpInputResponse.parse(McpJson.readTree("{\"action\":\"accept\",\"content\":[1]}")));
    }

    @Test
    void refusesAResponseThatIsNotAnObject() {
        assertThrows(McpProtocolException.class, () -> McpInputResponse.parse(McpJson.readTree("\"accept\"")));
    }
}
