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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpTransportHeadersTest {

    @Test
    void readsTheThreeHeadersThroughTheLookup() {
        Map<String, String> headers = Map.of(
                McpTransportHeaders.PROTOCOL_VERSION_HEADER, "2026-07-28",
                McpTransportHeaders.METHOD_HEADER, "tools/call",
                McpTransportHeaders.NAME_HEADER, "profiles_list");

        McpTransportHeaders read = McpTransportHeaders.read(headers::get);

        assertEquals(new McpTransportHeaders("2026-07-28", "tools/call", "profiles_list"), read);
    }

    @Test
    void namesTheHeadersAsTheSpecificationSpellsThem() {
        assertEquals("MCP-Protocol-Version", McpTransportHeaders.PROTOCOL_VERSION_HEADER);
        assertEquals("Mcp-Method", McpTransportHeaders.METHOD_HEADER);
        assertEquals("Mcp-Name", McpTransportHeaders.NAME_HEADER);
    }

    /**
     * Reading never fails: a malformed sentinel is refused when the request is validated, where the
     * refusal can be answered, not while the headers are copied out of the servlet request.
     */
    @Test
    void keepsTheRawValuesForValidationToDecode() {
        McpTransportHeaders read = McpTransportHeaders.read(
                Map.of(McpTransportHeaders.NAME_HEADER, "=?base64?broken")::get);

        assertEquals("=?base64?broken", read.name());
    }

    @Test
    void readsAbsentHeadersAsNull() {
        McpTransportHeaders read = McpTransportHeaders.read(name -> null);

        assertNull(read.protocolVersion());
        assertNull(read.method());
        assertNull(read.name());
    }
}
