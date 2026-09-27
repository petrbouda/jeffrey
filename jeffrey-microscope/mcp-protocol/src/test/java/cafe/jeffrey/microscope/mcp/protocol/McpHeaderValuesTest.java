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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpHeaderValuesTest {

    /** The name a refusal gives the header when the test does not say which one it read. */
    private static final String ANY_MCP_HEADER = "Mcp-*";

    @Test
    void passesAPlainValueThrough() {
        assertEquals("tools/call", McpHeaderValues.decode("tools/call", ANY_MCP_HEADER));
    }

    /**
     * The sentinel is matched exactly as the specification spells it. A value that only looks like it
     * in another case is not one; it is compared as the literal the client sent.
     */
    @Test
    void passesADifferentlyCasedSentinelThroughLiterally() {
        assertEquals("=?BASE64?YWI=?=", McpHeaderValues.decode("=?BASE64?YWI=?=", ANY_MCP_HEADER));
        assertEquals("=?Base64?YWI=?=", McpHeaderValues.decode("=?Base64?YWI=?=", ANY_MCP_HEADER));
    }

    @Test
    void passesAnAbsentValueThrough() {
        assertNull(McpHeaderValues.decode(null, ANY_MCP_HEADER));
    }

    /** A value HTTP cannot carry as-is travels as {@code =?base64?…?=}, UTF-8 underneath. */
    @Test
    void decodesTheBase64Sentinel() {
        String encoded = Base64.getEncoder().encodeToString("jfr_Grüße".getBytes(StandardCharsets.UTF_8));

        assertEquals("jfr_Grüße", McpHeaderValues.decode("=?base64?" + encoded + "?=", ANY_MCP_HEADER));
    }

    /** One padding character ({@code "ab"} is {@code YWI=}) and two ({@code "a"} is {@code YQ==}). */
    @Test
    void decodesPaddedBase64() {
        assertEquals("ab", McpHeaderValues.decode("=?base64?YWI=?=", ANY_MCP_HEADER));
        assertEquals("a", McpHeaderValues.decode("=?base64?YQ==?=", ANY_MCP_HEADER));
    }

    /** Real clients may leave the padding off; the value is the same either way. */
    @Test
    void decodesUnpaddedBase64() {
        assertEquals("ab", McpHeaderValues.decode("=?base64?YWI?=", ANY_MCP_HEADER));
        assertEquals("a", McpHeaderValues.decode("=?base64?YQ?=", ANY_MCP_HEADER));
    }

    @Test
    void decodesUnpaddedUtf8() {
        String encoded = Base64.getEncoder().withoutPadding()
                .encodeToString("jfr_Grüße".getBytes(StandardCharsets.UTF_8));

        assertEquals("jfr_Grüße", McpHeaderValues.decode("=?base64?" + encoded + "?=", ANY_MCP_HEADER));
    }

    /** Padding is all or nothing: a partial one is malformed, not a shorter value. */
    @Test
    void refusesPartialPadding() {
        McpProtocolException e = assertThrows(McpProtocolException.class,
                () -> McpHeaderValues.decode("=?base64?YQ=?=", ANY_MCP_HEADER));

        assertEquals(McpErrorCode.HEADER_MISMATCH, e.code());
    }

    /** One character past a full quantum carries fewer than eight bits: no byte can come of it. */
    @Test
    void refusesADanglingCharacter() {
        McpProtocolException e = assertThrows(McpProtocolException.class,
                () -> McpHeaderValues.decode("=?base64?YWJjZ?=", ANY_MCP_HEADER));

        assertEquals(McpErrorCode.HEADER_MISMATCH, e.code());
    }

    @Test
    void refusesBase64ThatDoesNotDecode() {
        McpProtocolException e = assertThrows(McpProtocolException.class,
                () -> McpHeaderValues.decode("=?base64?not*base64?=", ANY_MCP_HEADER));

        assertEquals(McpErrorCode.HEADER_MISMATCH, e.code());
    }

    @Test
    void refusesBytesThatAreNotUtf8() {
        String encoded = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xC3, (byte) 0x28});

        McpProtocolException e = assertThrows(McpProtocolException.class,
                () -> McpHeaderValues.decode("=?base64?" + encoded + "?=", ANY_MCP_HEADER));

        assertEquals(McpErrorCode.HEADER_MISMATCH, e.code());
    }

    /** Opened as the sentinel and never closed: malformed, not a literal that happens to start oddly. */
    @Test
    void refusesAnUnterminatedSentinel() {
        assertThrows(McpProtocolException.class, () -> McpHeaderValues.decode("=?base64?YWJj", ANY_MCP_HEADER));
    }
}
