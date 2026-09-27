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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpErrorCodeTest {

    @Test
    void carriesTheJsonRpcAndMcpCodes() {
        assertEquals(-32700, McpErrorCode.PARSE_ERROR.code());
        assertEquals(-32600, McpErrorCode.INVALID_REQUEST.code());
        assertEquals(-32601, McpErrorCode.METHOD_NOT_FOUND.code());
        assertEquals(-32602, McpErrorCode.INVALID_PARAMS.code());
        assertEquals(-32602, McpErrorCode.INVALID_META.code());
        assertEquals(-32603, McpErrorCode.INTERNAL.code());
        assertEquals(-32020, McpErrorCode.HEADER_MISMATCH.code());
        assertEquals(-32021, McpErrorCode.MISSING_CAPABILITY.code());
        assertEquals(-32022, McpErrorCode.UNSUPPORTED_VERSION.code());
    }

    /**
     * An unknown method is a 404, a request, {@code _meta} or header the transport refuses is a 400, and
     * an argument or internal error is answered inside a 200.
     */
    @Test
    void carriesOneHttpStatusPerCode() {
        assertEquals(400, McpErrorCode.PARSE_ERROR.httpStatus());
        assertEquals(400, McpErrorCode.INVALID_REQUEST.httpStatus());
        assertEquals(404, McpErrorCode.METHOD_NOT_FOUND.httpStatus());
        assertEquals(200, McpErrorCode.INVALID_PARAMS.httpStatus());
        assertEquals(400, McpErrorCode.INVALID_META.httpStatus());
        assertEquals(200, McpErrorCode.INTERNAL.httpStatus());
        assertEquals(400, McpErrorCode.HEADER_MISMATCH.httpStatus());
        assertEquals(400, McpErrorCode.MISSING_CAPABILITY.httpStatus());
        assertEquals(400, McpErrorCode.UNSUPPORTED_VERSION.httpStatus());
    }

    @Test
    void holdsExactlyTheNineCodesTheServerAnswersWith() {
        assertEquals(
                List.of("PARSE_ERROR", "INVALID_REQUEST", "METHOD_NOT_FOUND", "INVALID_PARAMS", "INVALID_META",
                        "INTERNAL", "HEADER_MISMATCH", "MISSING_CAPABILITY", "UNSUPPORTED_VERSION"),
                Arrays.stream(McpErrorCode.values()).map(Enum::name).toList());
    }

    /** {@code 2026-07-28} forbids {@code -32002}: a missing resource is answered with {@code -32602}. */
    @Test
    void neverAnswersWithTheResourceNotFoundCode() {
        assertFalse(Arrays.stream(McpErrorCode.values()).anyMatch(code -> code.code() == -32002));
    }
}
