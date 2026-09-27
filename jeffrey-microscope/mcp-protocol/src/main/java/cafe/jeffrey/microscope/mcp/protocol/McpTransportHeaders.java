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

import java.util.function.UnaryOperator;

/**
 * The HTTP headers the protocol reads, copied out of the request so the protocol layer never sees a
 * servlet type.
 * <p>
 * The values are kept raw. A {@code =?base64?…?=} value is decoded with {@link McpHeaderValues} when
 * the request is validated, where a malformed one can be answered with {@code -32020}; reading the
 * headers never fails. {@code Mcp-Param-*} headers are not read at all: they mirror parameters a tool
 * declares with {@code x-mcp-header}, which this protocol layer does not model.
 *
 * @param protocolVersion {@code MCP-Protocol-Version}, or null when absent
 * @param method          {@code Mcp-Method}, or null when absent
 * @param name            {@code Mcp-Name}, or null when absent
 */
public record McpTransportHeaders(String protocolVersion, String method, String name) {

    public static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    public static final String METHOD_HEADER = "Mcp-Method";
    public static final String NAME_HEADER = "Mcp-Name";

    /** No headers at all: a caller that reads none. */
    public static final McpTransportHeaders NONE = new McpTransportHeaders(null, null, null);

    /**
     * @param headerLookup the request's own header accessor, e.g. {@code httpRequest::getHeader}
     */
    public static McpTransportHeaders read(UnaryOperator<String> headerLookup) {
        return new McpTransportHeaders(
                headerLookup.apply(PROTOCOL_VERSION_HEADER),
                headerLookup.apply(METHOD_HEADER),
                headerLookup.apply(NAME_HEADER));
    }
}
